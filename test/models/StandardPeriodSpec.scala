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

package models

import base.SpecBase
import models.Period.getPeriod
import models.Quarter._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.mvc.PathBindable

import java.time.LocalDate
import java.time.Month._
import java.time.format.DateTimeFormatter

class StandardPeriodSpec
  extends SpecBase
    with ScalaCheckPropertyChecks
    with EitherValues {

  private val pathBindable = implicitly[PathBindable[Period]]

  "StandardPeriod" - {

    "must bind from a URL" in {

      forAll(arbitrary[Period]) {
        period =>

          pathBindable.bind("key", period.toString).value mustEqual period
      }
    }

    "must not bind from an invalid value" in {

      pathBindable.bind("key", "invalid").left.value mustEqual "Invalid period"
    }
  }

  ".firstDay" - {

    "must be the first of January when the quarter is Q1" in {

      forAll(Gen.choose(2022, 2100)) {
        year =>
          val period = StandardPeriod(year, Q1)
          period.firstDay mustEqual LocalDate.of(year, JANUARY, 1)
      }
    }

    "must be the first of April when the quarter is Q2" in {

      forAll(Gen.choose(2022, 2100)) {
        year =>
          val period = StandardPeriod(year, Q2)
          period.firstDay mustEqual LocalDate.of(year, APRIL, 1)
      }
    }

    "must be the first of July when the quarter is Q3" in {

      forAll(Gen.choose(2021, 2100)) {
        year =>
          val period = StandardPeriod(year, Q3)
          period.firstDay mustEqual LocalDate.of(year, JULY, 1)
      }
    }

    "must be the first of October when the quarter is Q4" in {

      forAll(Gen.choose(2021, 2100)) {
        year =>
          val period = StandardPeriod(year, Q4)
          period.firstDay mustEqual LocalDate.of(year, OCTOBER, 1)
      }
    }
  }

  ".lastDay" - {

    "must be the 31st of March when the quarter is Q1" in {

      forAll(Gen.choose(2022, 2100)) {
        year =>
          val period = StandardPeriod(year, Q1)
          period.lastDay mustEqual LocalDate.of(year, MARCH, 31)
      }
    }

    "must be the 30th of June when the quarter is Q2" in {

      forAll(Gen.choose(2022, 2100)) {
        year =>
          val period = StandardPeriod(year, Q2)
          period.lastDay mustEqual LocalDate.of(year, JUNE, 30)
      }
    }

    "must be the 30th of September when the quarter is Q3" in {

      forAll(Gen.choose(2021, 2100)) {
        year =>
          val period = StandardPeriod(year, Q3)
          period.lastDay mustEqual LocalDate.of(year, SEPTEMBER, 30)
      }
    }

    "must be the 31st of December when the quarter is Q4" in {

      forAll(Gen.choose(2021, 2100)) {
        year =>
          val period = StandardPeriod(year, Q4)
          period.lastDay mustEqual LocalDate.of(year, DECEMBER, 31)
      }
    }
  }

  ".getPeriod" - {

    "must return the correct period when given a LocalDate" in {

      val date: LocalDate = LocalDate.now(stubClock)
      val quarter: Quarter = Quarter.fromString(date.format(DateTimeFormatter.ofPattern("QQQ"))).get

      val expectedResult: Period = StandardPeriod(date.getYear, quarter)

      getPeriod(date) mustBe expectedResult
    }
  }
}
