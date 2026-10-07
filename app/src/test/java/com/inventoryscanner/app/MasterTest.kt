package com.inventoryscanner.app

import com.inventoryscanner.app.data.Master
import org.junit.Assert.assertEquals
import org.junit.Test

class MasterTest {
    @Test fun departmentCodesNormalise() {
        assertEquals("01", Master.deptCode("01"))
        assertEquals("01", Master.deptCode("1"))
        assertEquals("01", Master.deptCode("01-CGD"))
        assertEquals("01", Master.deptCode("FMCG"))
        assertEquals("02", Master.deptCode("Fresh Food"))
        assertEquals("03", Master.deptCode("LHH"))
        assertEquals("05", Master.deptCode("textile"))
        assertEquals("", Master.deptCode("99"))
        assertEquals("", Master.deptCode(""))
    }
}
