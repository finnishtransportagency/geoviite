package fi.fta.geoviite.infra.inframodel

import java.io.File
import java.io.InputStream

object DummyFileContent {

    fun dummyValidFileXmlAsInputStream(): InputStream {
        val resource = InfraModel::class.java.getResource("/inframodel/testfile_simple.xml")!!
        return File(resource.toURI()).inputStream()
    }

    fun dummyInvalidFileXmlAsInputStream(): InputStream {
        val resource = InfraModel::class.java.getResource("/inframodel/testfile_broken.xml")!!
        return File(resource.toURI()).inputStream()
    }
}
