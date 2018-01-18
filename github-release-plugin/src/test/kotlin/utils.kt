package cy.github.tests

import cy.github.*
import kotlin.test.*
import org.junit.Test as test

class TestSCMParse {
    @test fun testSimple() {
        assertEquals(
                Repo("https://api.github.com", "cy6erGn0m", "github-release-plugin"),
                "scm:git:git@github.com:cy6erGn0m/github-release-plugin.git".parseSCMUrl())
        assertEquals(
                Repo("https://api.github.com", "cy6erGn0m", "github-release-plugin"),
                "scm:git:https://github.com/cy6erGn0m/github-release-plugin.git".parseSCMUrl())
        assertEquals(
                Repo("https://api.github.com", "cy6erGn0m", "github-release-plugin"),
                "scm:git:ssh://git@github.com/cy6erGn0m/github-release-plugin.git".parseSCMUrl())
    }
}
