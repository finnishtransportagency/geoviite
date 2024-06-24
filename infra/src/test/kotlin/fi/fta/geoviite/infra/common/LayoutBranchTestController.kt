package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.aspects.GeoviiteController
import org.springframework.web.bind.annotation.*

data class MainBranchTestObject(val branch: MainBranch) {
    val type: String = "main"
}

data class DesignBranchTestObject(val branch: DesignBranch) {
    val type: String = "design"
}

data class LayoutBranchTestObject(val branch: LayoutBranch) {
    val type: String = "baseclass"
}

const val LAYOUT_TEST_URL = "/layout-branch-test"

@GeoviiteController(LAYOUT_TEST_URL)
class LayoutBranchTestController {

    @GetMapping("/{branch}")
    fun requestWithBranchPath(@PathVariable("branch") branch: LayoutBranch): LayoutBranch = branch

    @GetMapping("/main/{branch}")
    fun requestWithBranchPath(@PathVariable("branch") branch: MainBranch): MainBranch = branch

    @GetMapping("/design/{branch}")
    fun requestWithBranchPath(@PathVariable("branch") branch: DesignBranch): DesignBranch = branch

    @GetMapping("/arg")
    fun requestWithBranchArgument(@RequestParam("branch") branch: LayoutBranch): LayoutBranch = branch

    @GetMapping("/arg/main")
    fun requestWithBranchArgument(@RequestParam("branch") branch: MainBranch): MainBranch = branch

    @GetMapping("/arg/design")
    fun requestWithBranchArgument(@RequestParam("branch") branch: DesignBranch): DesignBranch = branch

    @PostMapping("/body")
    fun requestWithBranchBody(@RequestBody body: LayoutBranchTestObject): LayoutBranchTestObject = body

    @PostMapping("/body/main")
    fun requestWithBranchBody(@RequestBody body: MainBranchTestObject): MainBranchTestObject = body

    @PostMapping("/body/design")
    fun requestWithBranchBody(@RequestBody body: DesignBranchTestObject): DesignBranchTestObject = body
}
