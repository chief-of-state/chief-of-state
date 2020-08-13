package com.namely.chiefofstate

import com.google.protobuf.timestamp.Timestamp
import com.namely.protobuf.chief_of_state.common.{MetaData => CosMetaData}
import io.superflat.lagompb.protobuf.core.{MetaData => LagompbMetaData}
import io.superflat.lagompb.testkit.BaseSpec

class UtilSpec extends BaseSpec {

  "toCosMetaData" should {
    "return the right COS MetaData" in {
      val ts = Timestamp().withSeconds(3L).withNanos(2)
      val revisionNumber = 2L
      val data = Map("foo" -> "bar")

      val lagomMetaData = LagompbMetaData()
        .withRevisionNumber(revisionNumber)
        .withRevisionDate(ts)
        .withData(data)

      val expected = CosMetaData()
        .withRevisionNumber(revisionNumber)
        .withRevisionDate(ts)
        .withData(data)

      val actual = Util.toCosMetaData(lagomMetaData)

      actual shouldBe (expected)
    }
  }
}
