package com.datamountaineer.streamreactor.connect.rethink.sink

import com.datamountaineeer.streamreactor.connect.rethink.config.{ReThinkSettings, ReThinkSinkConfig}
import com.datamountaineeer.streamreactor.connect.rethink.sink.ReThinkWriter
import com.datamountaineer.streamreactor.connect.rethink.TestBase
import com.datamountaineer.streamreactor.connect.schemas.ConverterUtil
import com.rethinkdb.RethinkDB
import com.rethinkdb.gen.ast._
import com.rethinkdb.model.MapObject
import com.rethinkdb.net.Connection
import org.apache.kafka.connect.errors.RetriableException
import org.apache.kafka.connect.sink.SinkTaskContext
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.mockito.Matchers._

import scala.collection.JavaConverters._

/**
  * Created by andrew@datamountaineer.com on 21/06/16. 
  * stream-reactor-maven
  */
class TestReThinkWriter extends TestBase with MockitoSugar with ConverterUtil {
  "should write to rethink" in {
    val context = mock[SinkTaskContext]
    when(context.assignment()).thenReturn(getAssignment)
    val config = new ReThinkSinkConfig(getProps)
    val settings = ReThinkSettings(config)
    val records = getTestRecords

    val conflict = settings.conflictPolicy.get(TABLE).get

    val r = mock[RethinkDB]
    val connBuilder = mock[Connection.Builder]
    val conn = mock[Connection]
    val mo = new MapObject
    when(r.connection()).thenReturn(connBuilder)
    when(connBuilder.connect()).thenReturn(conn)
    when(conn.isOpen).thenReturn(true)
    when(r.hashMap()).thenReturn(mo)

    val db = mock[Db]
    val table = mock[Table]
    val insert = mock[Insert]
    val tableList = mock[TableList]
    val tableCreate = mock[TableCreate]

    when(r.db(DB)).thenReturn(db)
    when(db.tableList()).thenReturn(tableList)
    when(r.db(DB).tableList().run(conn)).thenReturn(List(TABLE).asJava)

    when(db.tableCreate(TABLE)).thenReturn(tableCreate)
    when(tableCreate.run(conn)).thenReturn(new java.util.HashMap[String, Object])

    when(db.table(TABLE)).thenReturn(table)
    when(table.insert(any[String])).thenReturn(insert)
    when(insert.optArg("conflict", conflict.toLowerCase)).thenReturn(insert)
    when(insert.optArg("return_changes", true)).thenReturn(insert)
    when(insert.run(conn)).thenReturn(new java.util.HashMap[String, Object])

    val writer = new ReThinkWriter(r, conn, settings)
    writer.write(records = records)
  }

  "should handle retry error" in {
    val context = mock[SinkTaskContext]
    when(context.assignment()).thenReturn(getAssignment)
    val config = new ReThinkSinkConfig(getPropsUpsertSelectRetry)
    val settings = ReThinkSettings(config)
    val records = getTestRecords
    val conflict = settings.conflictPolicy.get(TABLE).get

    val r = mock[RethinkDB]
    val connBuilder = mock[Connection.Builder]
    val conn = mock[Connection]
    val mo = new MapObject
    when(r.connection()).thenReturn(connBuilder)
    when(connBuilder.connect()).thenReturn(conn)
    when(conn.isOpen).thenReturn(true)
    when(r.hashMap()).thenReturn(mo)

    val db = mock[Db]
    val table = mock[Table]
    val insert = mock[Insert]
    val tableList = mock[TableList]
    val tableCreate = mock[TableCreate]

    when(r.db(DB)).thenReturn(db)
    when(db.tableList()).thenReturn(tableList)
    when(tableList.run(conn)).thenReturn(List(TABLE).asJava)
    when(db.tableCreate(TABLE)).thenReturn(tableCreate)
    when(tableCreate.run(conn)).thenReturn(new java.util.HashMap[String, Object])

    when(db.table(TABLE)).thenReturn(table)
    when(table.insert(any[String])).thenReturn(insert)
    when(insert.optArg("conflict", conflict.toLowerCase)).thenReturn(insert)
    when(insert.optArg("return_changes", true)).thenReturn(insert)

    val ret  = new java.util.HashMap[String, Object]()
    ret.put("errors","1")
    ret.put("first_error", "test_error")
    when(insert.run(conn)).thenReturn(ret)

    val writer = new ReThinkWriter(r, conn, settings)
    intercept[RetriableException] {
      writer.write(records = records)
    }
  }
}
