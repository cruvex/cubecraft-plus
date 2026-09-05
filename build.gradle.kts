import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("cubecraft-plus") {
			sourceSet(sourceSets.getByName("client"))
		}
	}

	log4jConfigs.from("log4j-dev.xml")
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// Refreshes the offline seed the mod falls back to before its first successful fetch.
// The API serves Cubepanion's games.yaml already merged with the ids it assigns, so one
// request is enough - the YAML on its own carries no ids, which the seed depends on.
tasks.register("updateBundledGames") {
	group = "cubecraft-plus"
	description = "Refreshes the bundled games.json offline seed from the Cubepanion API"

	val endpoint = (findProperty("games_url") as String?) ?: "https://cubepanion.ameliah.art/api/v2/Games"
	val target = layout.projectDirectory.file("src/main/resources/assets/cubecraft-plus/games.json").asFile
	val projectDir = layout.projectDirectory.asFile
	// Only what the Game record models; the API also sends icon and gameFlags
	val fields = listOf("id", "name", "displayName", "aliases", "active", "scoreType", "shouldTrack", "hasPreLobby", "enabledFlags")

	doLast {
		val response = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.header("Accept", "application/json")
				.header("User-Agent", "CubeCraftPlus-build")
				.GET()
				.build(),
			HttpResponse.BodyHandlers.ofString()
		)
		if (response.statusCode() != 200) {
			throw GradleException("$endpoint returned ${response.statusCode()}")
		}

		@Suppress("UNCHECKED_CAST")
		val games = JsonSlurper().parseText(response.body()) as? List<Map<String, Any?>>
			?: throw GradleException("$endpoint did not return a JSON array")
		if (games.isEmpty()) {
			throw GradleException("$endpoint returned no games, refusing to overwrite the seed")
		}

		val seed = games.sortedBy { (it["id"] as Number).toInt() }.map { game ->
			val missing = fields.filterNot(game::containsKey)
			if (missing.isNotEmpty()) {
				throw GradleException("Game ${game["name"] ?: game} is missing $missing")
			}
			// Fixed key order, so a reshuffle upstream cannot churn the diff
			fields.associateWith { game[it] }
		}

		val ids = seed.map { it["id"] }
		if (ids.toSet().size != ids.size) {
			throw GradleException("Duplicate game ids from $endpoint: $ids")
		}

		target.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(seed)) + "\n")
		logger.lifecycle("Wrote ${seed.size} games to ${target.relativeTo(projectDir)}")
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
