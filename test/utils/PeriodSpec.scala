package utils

import base.SpecBase
import models.Period.isThreeYearsOld

import java.time.LocalDate

class PeriodSpec extends SpecBase {

  val year: Int = 2024

  "isThreeYearsOld" - {
    "should return true" - {
      "when there is a DueDate exactly three years old" in {

        val dueDate: LocalDate = LocalDate.now().minusYears(3)

        isThreeYearsOld(dueDate, stubClock) mustBe true
      }

      "when there is a DueDate more than three years old" in {
        val dueDate = LocalDate.now().minusYears(3).minusDays(1)

        isThreeYearsOld(dueDate, stubClock) mustBe true
      }
    }

    "should return false" - {
      "when the DueDate is not three year old" in {
        val dueDate = LocalDate.now()

        isThreeYearsOld(dueDate, stubClock) mustBe false
      }
    }
  }
}