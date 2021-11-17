package utils

object StringUtils {

  def rotateDigitsInString(chars: String): String =
    chars.map {
      char =>
        (char.asDigit + 1) % 10
    }.mkString


}
