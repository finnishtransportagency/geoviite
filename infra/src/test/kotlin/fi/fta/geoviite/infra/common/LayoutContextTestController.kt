package fi.fta.geoviite.infra.common

import org.springframework.web.bind.annotation.*

data class MainContextTestObject(val context: MainLayoutContext) {
    val type: String = "main"
}

data class DesignContextTestObject(val context: DesignLayoutContext) {
    val type: String = "design"
}

data class LayoutContextTestObject(val context: LayoutContext) {
    val type: String = "baseclass"
}

@RestController
@RequestMapping("/layout-context-test")
class LayoutContextTestController {

    @GetMapping("/{context}")
    fun requestWithContextPath(@PathVariable("context") context: LayoutContext): LayoutContextTestObject {
        return LayoutContextTestObject(context)
    }

    @GetMapping("/main/{context}")
    fun requestWithContextPath(@PathVariable("context") context: MainLayoutContext): MainContextTestObject {
        return MainContextTestObject(context)
    }

    @GetMapping("/design/{context}")
    fun requestWithContextPath(@PathVariable("context") context: DesignLayoutContext): DesignContextTestObject {
        return DesignContextTestObject(context)
    }

    @GetMapping("/arg")
    fun requestWithContextArgument(@RequestParam("context") context: LayoutContext): LayoutContextTestObject {
        return LayoutContextTestObject(context)
    }

    @GetMapping("/arg/main")
    fun requestWithContextArgument(@RequestParam("context") context: MainLayoutContext): MainContextTestObject {
        return MainContextTestObject(context)
    }

    @GetMapping("/arg/design")
    fun requestWithContextArgument(@RequestParam("context") context: DesignLayoutContext): DesignContextTestObject {
        return DesignContextTestObject(context)
    }

    @PostMapping("/body")
    fun requestWithContextBody(@RequestBody body: LayoutContextTestObject): LayoutContextTestObject {
        return body
    }

    @PostMapping("/body/main")
    fun requestWithContextBody(@RequestBody body: MainContextTestObject): MainContextTestObject {
        return body
    }

    @PostMapping("/body/design")
    fun requestWithContextBody(@RequestBody body: DesignContextTestObject): DesignContextTestObject {
        return body
    }
}
