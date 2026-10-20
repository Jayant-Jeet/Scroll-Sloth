package com.reelsloth.data

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {
    @Test
    fun writesHeaderAndOneRowPerEventInLocalTime() {
        val out = StringBuilder()
        CsvExporter.write(
            listOf(
                ReelEventEntity(id = 1, timestampMs = 1_792_454_400_000, platform = "INSTAGRAM", dwellMs = 4_200),
                ReelEventEntity(id = 2, timestampMs = 1_792_454_410_000, platform = "YOUTUBE", dwellMs = 120_000),
            ),
            ZoneId.of("Asia/Kolkata"),
            out,
        )

        assertEquals(
            "timestamp_ms,local_time,platform,dwell_ms\n" +
                "1792454400000,2026-10-20T05:30:00+05:30,INSTAGRAM,4200\n" +
                "1792454410000,2026-10-20T05:30:10+05:30,YOUTUBE,120000\n",
            out.toString(),
        )
    }

    @Test
    fun emptyExportIsJustTheHeader() {
        val out = StringBuilder()
        CsvExporter.write(emptyList(), ZoneId.of("UTC"), out)
        assertEquals("timestamp_ms,local_time,platform,dwell_ms\n", out.toString())
    }

    @Test
    fun unexpectedValuesAreQuotedAndFormulasDefused() {
        assertEquals("FACEBOOK", CsvExporter.cell("FACEBOOK"))
        assertEquals("\"a,b\"", CsvExporter.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""))
        assertEquals("'=1+1", CsvExporter.cell("=1+1"))
        assertEquals("\"'=A1,B1\"", CsvExporter.cell("=A1,B1"))
    }
}
