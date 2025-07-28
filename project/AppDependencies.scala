import play.core.PlayVersion
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.18.0"
  private val hmrcMongoVersion = "2.7.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc"             %% "domain-play-30"             % "10.0.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % hmrcMongoVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion,
    "org.scalatest"           %% "scalatest"                  % "3.2.19",
    "org.playframework"       %% "play-test"                  % PlayVersion.current,
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "7.0.2",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0",
    "org.scalatestplus"       %% "mockito-4-11"               % "3.2.18.0"
  ).map(_ % "test, it")
}
