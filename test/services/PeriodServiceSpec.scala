/*
 * Copyright 2021 HM Revenue & Customs
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

package services

import base.SpecBase
import models.Quarter._
import models.{PeriodYear, StandardPeriod}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.time.{Clock, Instant, LocalDate, ZoneId}

class PeriodServiceSpec extends SpecBase with ScalaCheckPropertyChecks {

  ".getAvailablePeriods" - {

    "when today is 11th October" - {

      val instant = Instant.ofEpochSecond(1633959834)
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      "should return Q3 for commencement date of 30th September" in {
        val commencementDate = LocalDate.of(2021, 9, 30)

        val service = new PeriodService(stubClock)

        val expectedPeriods = Seq(StandardPeriod(2021, Q3))

        service.getReturnPeriods(commencementDate) must contain theSameElementsAs expectedPeriods
      }

      "should return nothing for commencement date of 10th October" in {
        val commencementDate = LocalDate.of(2021, 10, 10)

        val service = new PeriodService(stubClock)

        val expectedPeriods = Seq.empty

        service.getReturnPeriods(commencementDate) must contain theSameElementsAs expectedPeriods
      }
    }

  }

  ".getPeriodYears" - {
    "when today is 11th October 2021" - {
      val instant = Instant.ofEpochSecond(1633959834)
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      "should return 2021 for commencement date of 30th September" in {
        val commencementDate = LocalDate.of(2021, 9, 30)

        val service = new PeriodService(stubClock)

        val expectedTaxYears = Seq(PeriodYear(2021))

        service.getPeriodYears(commencementDate) must contain theSameElementsAs expectedTaxYears
      }

    }
    "when today is 7th April 2022" - {
      val instant = Instant.ofEpochSecond(1649332800)
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      "should return 2021 and 2022 for commencement date of 30th September" in {
        val commencementDate = LocalDate.of(2021, 9, 30)

        val service = new PeriodService(stubClock)

        val expectedTaxYears = Seq(PeriodYear(2021), PeriodYear(2022))

        service.getPeriodYears(commencementDate) must contain theSameElementsAs expectedTaxYears
      }

    }
    "when today is 11th October 2022" - {
      val instant = Instant.ofEpochSecond(1665489600)
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      "should return 2021 and 2022 for commencement date of 30th September" in {
        val commencementDate = LocalDate.of(2021, 9, 30)

        val service = new PeriodService(stubClock)

        val expectedTaxYears = Seq(PeriodYear(2021), PeriodYear(2022))

        service.getPeriodYears(commencementDate) must contain theSameElementsAs expectedTaxYears
      }

    }
  }

  ".getAllPeriods" - {
    "when today is 11th October" in {
      val instant = Instant.ofEpochSecond(1633959834)
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      val service = new PeriodService(stubClock)

      val expectedPeriods = Seq(StandardPeriod(2021, Q3))

      service.getAllPeriods(commencementDate) must contain theSameElementsAs expectedPeriods
    }

    "when today is 11th January" in {
      val instant = Instant.parse("2022-01-11T12:00:00Z")

      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

      val service = new PeriodService(stubClock)

      val expectedPeriods = Seq(StandardPeriod(2021, Q3), StandardPeriod(2021, Q4))

      service.getAllPeriods(LocalDate.of(2021, 7, 1)) must contain theSameElementsAs expectedPeriods
    }
  }
  ".getNextPeriod" - {
    "when current period is Q1" in {
      val year = 2021
      val current = StandardPeriod(year, Q1)
      val expected = StandardPeriod(year, Q2)
      val instant = Instant.parse("2022-01-11T12:00:00Z")
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
      val service = new PeriodService(stubClock)
      service.getNextPeriod(current) mustBe expected
    }

    "when current period is Q2" in {
      val year = 2021
      val current = StandardPeriod(year, Q2)
      val expected = StandardPeriod(year, Q3)
      val instant = Instant.parse("2022-01-11T12:00:00Z")
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
      val service = new PeriodService(stubClock)
      service.getNextPeriod(current) mustBe expected
    }

    "when current period is Q3" in {
      val year = 2021
      val current = StandardPeriod(year, Q3)
      val expected = StandardPeriod(year, Q4)
      val instant = Instant.parse("2022-01-11T12:00:00Z")
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
      val service = new PeriodService(stubClock)
      service.getNextPeriod(current) mustBe expected
    }

    "when current period is Q4" in {
      val year = 2021
      val current = StandardPeriod(year, Q4)
      val expected = StandardPeriod(year + 1, Q1)
      val instant = Instant.parse("2022-01-11T12:00:00Z")
      val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
      val service = new PeriodService(stubClock)
      service.getNextPeriod(current) mustBe expected
    }
  }
}