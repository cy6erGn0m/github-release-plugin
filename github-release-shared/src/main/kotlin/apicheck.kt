package cy.github

import java.io.File

/*
fun main(args: Array<String>) {
    val auth = Auth("cy6ergn0m", "1d90f50c073a91253e5c1c2a56a74427c5a17439")
    val repo = Repo("cy6erGn0m", "html4k")
    val tagName = "kotlinx.html-0.4.12"

    val repositoryFormat = probeGitHubRepositoryFormat("https://api.github.com", auth)
    val releasesFormat = probeGitHubReleasesFormat(repositoryFormat, repo, auth)

    val release = findRelease(releasesFormat, tagName, auth) ?: createRelease(auth, releasesFormat, tagName, "Test release API", "", true)
        ?: throw IllegalArgumentException("failed to get release")

    println(release)

    println("Uploading file...")
    upload(auth, release.uploadFormat, File("src/rfc6570.kt"))

    println("Done")
    println("See ${release.htmlPage}")
}
*/