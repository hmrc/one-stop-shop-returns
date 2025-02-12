/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import config.AppConfig
import connectors.VatReturnConnector
import controllers.actions.AuthenticatedControllerComponents
import logging.Logging
import models.Period.isThreeYearsOld
import models.SubmissionStatus.{Complete, Excluded, Expired}
import models.etmp.EtmpObligationsQueryParameters
import models.exclusions.ExcludedTrader
import models.yourAccount._
import models.{Period, PeriodWithStatus, SubmissionStatus}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.SaveForLaterRepository
import services.exclusions.ExclusionService
import services.{PeriodService, VatReturnService}
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.Formatters.etmpDateFormatter

import java.time.{Clock, LocalDate}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ReturnStatusController @Inject()(
                                        cc: AuthenticatedControllerComponents,
                                        config: AppConfig,
                                        vatReturnService: VatReturnService,
                                        periodService: PeriodService,
                                        exclusionService: ExclusionService,
                                        saveForLaterRepository: SaveForLaterRepository,
                                        vatReturnConnector: VatReturnConnector,
                                        clock: Clock
                                      )(implicit ec: ExecutionContext)
  extends BackendController(cc) with Logging {

  def listStatuses(commencementLocalDate: LocalDate): Action[AnyContent] = cc.authAndGetRegistration().async {
    implicit request =>
      val periodWithStatuses = getStatuses(commencementLocalDate, request.vrn, request.registration.excludedTrader)

      periodWithStatuses.map { pws =>
        Ok(Json.toJson(pws))
      }
  }

  def getCurrentReturns(vrn: String): Action[AnyContent] = cc.authAndGetRegistration(vrn).async {
    implicit request =>
      for {
        availablePeriodsWithStatus <- getStatuses(request.registration.commencementDate, request.vrn, request.registration.excludedTrader)
        savedAnswers <- saveForLaterRepository.get(request.vrn)
        finalReturnsCompleted <- exclusionService.hasSubmittedFinalReturn()
      } yield {
        val answers = savedAnswers.sortBy(_.lastUpdated).lastOption

        val incompletePeriods = availablePeriodsWithStatus.filterNot(pws => Seq(Complete, Excluded, Expired).contains(pws.status))
        val excludedReturnsPeriods = availablePeriodsWithStatus.filter(period => Seq(Excluded, Expired).contains(period.status))

        val isExcluded = request.registration.excludedTrader.exists(_.isExcludedNotReversed)

        val periodInProgress = answers.map(answer => answer.period)
        val oldestPeriod = incompletePeriods.sortBy(_.period).headOption
        val oldestExcludedPeriod = excludedReturnsPeriods.sortBy(_.period).headOption

        def periodsWithStatusToReturns(periods: Seq[PeriodWithStatus]) = {
          periods.sortBy(_.period).map(
            periodWithStatus => Return.fromPeriod(
              periodWithStatus.period,
              periodWithStatus.status,
              periodInProgress.contains(periodWithStatus.period),
              if (Seq(Excluded, Expired).contains(periodWithStatus.status)) {
                oldestExcludedPeriod.contains(periodWithStatus)
              } else {
                oldestPeriod.contains(periodWithStatus)
              }
            )
          )
        }

        val returns = periodsWithStatusToReturns(incompletePeriods)
        val excludedReturns = periodsWithStatusToReturns(excludedReturnsPeriods)

        Ok(Json.toJson(CurrentReturns(returns, isExcluded, finalReturnsCompleted, excludedReturns)))
      }
  }

  private def getStatuses(commencementLocalDate: LocalDate, vrn: Vrn, excludedTrader: Option[ExcludedTrader]): Future[Seq[PeriodWithStatus]] = {

    val etmpObligationsQueryParameters = EtmpObligationsQueryParameters(
      fromDate = commencementLocalDate.format(etmpDateFormatter),
      toDate = LocalDate.now(clock).plusMonths(1).withDayOfMonth(1).minusDays(1).format(etmpDateFormatter),
      status = None

    )
    val futureFulfilledPeriods: Future[Seq[Period]] = if (config.strategicReturnApiEnabled) {
      vatReturnConnector.getObligations(vrn.vrn, etmpObligationsQueryParameters).map {
        case Right(obligations) =>
          val g = obligations.getFulfilledPeriods
          println(s"g $g")
          g
        case x =>
          logger.error(s"Error when getting obligations for return status' $x")
          throw new Exception("Error getting obligations for status")
      }
    } else {
      vatReturnService.get(vrn).map(x => x.map(y => y.period))
    }

    for {
      periods <- Future {
        periodService.getReturnPeriods(commencementLocalDate)
      }
      runningPeriod <- Future {
        periodService.getRunningPeriod(LocalDate.now(clock))
      }
      nextPeriod <- Future {
        if (periods.nonEmpty) {
          periodService.getNextPeriod(
            periods.maxBy(_.lastDay.toEpochDay)
          )
        } else {
          if (commencementLocalDate.isAfter(runningPeriod.lastDay)) {
            periodService.getRunningPeriod(commencementLocalDate)
          } else {
            runningPeriod
          }
        }
      }
      fulfilledPeriods <- futureFulfilledPeriods
    } yield {

      val currentPeriods = periods.map {
        period =>
          if (fulfilledPeriods.contains(period)) {
            PeriodWithStatus(period, SubmissionStatus.Complete)
          } else if (isPeriodExcluded(period, excludedTrader)) {
            PeriodWithStatus(period, SubmissionStatus.Excluded)
          } else if (isPeriodExpired(period, excludedTrader)) {
            PeriodWithStatus(period, SubmissionStatus.Expired)
          } else {
            if (LocalDate.now(clock).isAfter(period.paymentDeadline)) {
              PeriodWithStatus(period, SubmissionStatus.Overdue)
            } else {
              PeriodWithStatus(period, SubmissionStatus.Due)
            }
          }
      }
      println(s"fulfilledPeriods $fulfilledPeriods")
      println(s"periods $periods")
      println(s"runningPeriod $runningPeriod")
      println(s"nextPeriod $nextPeriod")
      println(s"currentPeriods $currentPeriods")
      if (currentPeriods.forall(_.status == Complete)) {
        currentPeriods ++ Seq(PeriodWithStatus(nextPeriod, SubmissionStatus.Next))
      } else {
        currentPeriods
      }
    }
  }

  private def isPeriodExcluded(period: Period, excludedTrader: Option[ExcludedTrader]): Boolean = {
    excludedTrader match {
      case Some(excluded) if excluded.isExcludedNotReversed && period.lastDay.isAfter(periodService.getNextPeriod(excluded.finalReturnPeriod).firstDay) =>
        true
      case _ => false
    }
  }

  private def isPeriodExpired(period: Period, excludedTrader: Option[ExcludedTrader]): Boolean = {
    excludedTrader match {
      case Some(excluded) if excluded.isExcludedNotReversed && isThreeYearsOld(period.paymentDeadline, clock) =>
        true
      case _ => false
    }
  }
}

