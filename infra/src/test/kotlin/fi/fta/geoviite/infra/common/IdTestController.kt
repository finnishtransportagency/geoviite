package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.aspects.GeoviiteController
import org.springframework.web.bind.annotation.*

data class IdTestObject(val id: DomainId<IdTestObject>)

data class StringIdTestObject(val id: StringId<IdTestObject>)

data class IntIdTestObject(val id: IntId<IdTestObject>)

data class IndexedIdTestObject(val id: IndexedId<IdTestObject>)

data class OidTestObject(val id: Oid<OidTestObject>)

data class SridTestObject(val id: Srid)

const val ID_TEST_URL = "/id-test"

@GeoviiteController(ID_TEST_URL)
class IdTestController {

    @GetMapping("/{id}")
    fun requestWithIdPath(@PathVariable("id") id: DomainId<IdTestObject>): IdTestObject {
        return IdTestObject(id)
    }

    @GetMapping("/string/{id}")
    fun requestWithIdPath(@PathVariable("id") id: StringId<IdTestObject>): StringIdTestObject {
        return StringIdTestObject(id)
    }

    @GetMapping("/int/{id}")
    fun requestWithIdPath(@PathVariable("id") id: IntId<IdTestObject>): IntIdTestObject {
        return IntIdTestObject(id)
    }

    @GetMapping("/indexed/{id}")
    fun requestWithIdPath(@PathVariable("id") id: IndexedId<IdTestObject>): IndexedIdTestObject {
        return IndexedIdTestObject(id)
    }

    @GetMapping("/id-test-arg")
    fun requestWithIdArgument(@RequestParam("id") id: DomainId<IdTestObject>): IdTestObject {
        return IdTestObject(id)
    }

    @GetMapping("/id-test-arg/string")
    fun requestWithIdArgument(@RequestParam("id") id: StringId<IdTestObject>): StringIdTestObject {
        return StringIdTestObject(id)
    }

    @GetMapping("/id-test-arg/int")
    fun requestWithIdArgument(@RequestParam("id") id: IntId<IdTestObject>): IntIdTestObject {
        return IntIdTestObject(id)
    }

    @GetMapping("/id-test-arg/indexed")
    fun requestWithIdArgument(@RequestParam("id") id: IndexedId<IdTestObject>): IndexedIdTestObject {
        return IndexedIdTestObject(id)
    }

    @PostMapping("/id-test-body")
    fun requestWithIdBody(@RequestBody body: IdTestObject): IdTestObject {
        return body
    }

    @PostMapping("/id-test-body/string")
    fun requestWithStringIdBody(@RequestBody body: StringIdTestObject): StringIdTestObject {
        return body
    }

    @PostMapping("/id-test-body/int")
    fun requestWithIntIdBody(@RequestBody body: IntIdTestObject): IntIdTestObject {
        return body
    }

    @PostMapping("/id-test-body/indexed")
    fun requestWithIndexedIdBody(@RequestBody body: IndexedIdTestObject): IndexedIdTestObject {
        return body
    }

    @GetMapping("/oid/{id}")
    fun requestWithOidPath(@PathVariable("id") id: Oid<OidTestObject>): OidTestObject {
        return OidTestObject(id)
    }

    @GetMapping("/oid-test-arg")
    fun requestWithOidArgument(@RequestParam("id") id: Oid<OidTestObject>): OidTestObject {
        return OidTestObject(id)
    }

    @PostMapping("/oid-test-body")
    fun requestWithOidBody(@RequestBody body: OidTestObject): OidTestObject {
        return body
    }

    @GetMapping("/srid/{id}")
    fun requestWithSridPath(@PathVariable("id") id: Srid): SridTestObject {
        return SridTestObject(id)
    }

    @GetMapping("/srid-test-arg")
    fun requestWithSridArgument(@RequestParam("id") id: Srid): SridTestObject {
        return SridTestObject(id)
    }

    @PostMapping("/srid-test-body")
    fun requestWithSridBody(@RequestBody body: SridTestObject): SridTestObject {
        return body
    }
}
