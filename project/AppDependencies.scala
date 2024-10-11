import play.core.PlayVersion
import sbt.*

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % "9.5.0",
    "uk.gov.hmrc"             %% "domain-play-30"             % "10.0.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % "2.2.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % "9.5.0",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % "2.2.0",
    "org.scalatest"           %% "scalatest"                  % "3.2.19",
    "org.playframework"       %% "play-test"                  % PlayVersion.current,
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.64.8",
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "7.0.1",
    "org.scalatestplus"       %% "scalacheck-1-15"            % "3.2.11.0",
    "org.scalatestplus"       %% "mockito-4-6"                % "3.2.15.0",
    "org.mockito"             %% "mockito-scala"              % "1.17.37"
  ).map(_ % "test, it")
}
