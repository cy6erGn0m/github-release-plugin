package cy.github.tests

import org.junit.Test as test
import cy.github.*
import kotlin.test.assertEquals

class TestSCMParse {
    test fun testSimple() {
        assertEquals(
                Repo("https://api.github.com", "cy6erGn0m", "github-release-plugin"),
                "scm:git:git@github.com:cy6erGn0m/github-release-plugin.git".parseSCMUrl())
    }
}
