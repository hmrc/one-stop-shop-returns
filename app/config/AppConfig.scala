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

package config

import models.Period
import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(config: Configuration) {

  val appName: String = config.get[String]("appName")

  val encryptionKey: String = config.get[String]("mongodb.encryption.key")
  val cacheTtl: Int = config.get[Int]("mongodb.timeToLiveInDays")

  val coreVatReturnsEnabled: Boolean = config.get[Boolean]("features.coreVatReturns")
  val historicCoreVatReturnsEnabled: Boolean = config.get[Boolean]("features.historicCoreVatReturns")
  val historicPeriodsToSubmit: Seq[Period] = config.get[Seq[String]]("historicPeriodsToSubmit").flatMap(Period.fromString)
  val historicCoreVatReturnIndexFilteringEnabled: Boolean = config.get[Boolean]("historicCoreVatReturns.indexFiltering")
  val historicCoreVatReturnStartIdx: Int = config.get[Int]("historicCoreVatReturns.startIdx")
  val historicCoreVatReturnEndIdx: Int = config.get[Int]("historicCoreVatReturns.endIdx")
  val historicCoreVatReturnIndexesToInclude : Seq[Int] = config.get[Seq[Int]]("historicCoreVatReturns.includeIndexes")
  val historicCoreVatReturnIndexesToExclude : Seq[Int] = config.get[Seq[Int]]("historicCoreVatReturns.excludeIndexes")
  val historicCoreVatReturnReferencesEnabled: Boolean = config.get[Boolean]("historicCoreVatReturns.referenceFiltering.enabled")
  val historicCoreVatReturnReferences: Seq[String] = config.get[Seq[String]]("historicCoreVatReturns.referenceFiltering.references")

  val ossEnrolment: String = config.get[String]("oss-enrolment")
  val ossEnrolmentEnabled: Boolean = config.get[Boolean]("features.oss-enrolment")

  val exclusionsEnabled: Boolean = config.get[Boolean]("features.exclusions.enabled")

  val externalEntryTtlDays: Int = config.get[Int]("features.externalEntry.ttlInDays")

  val strategicReturnApiEnabled: Boolean = config.get[Boolean]("features.strategic-returns.enabled")
}
