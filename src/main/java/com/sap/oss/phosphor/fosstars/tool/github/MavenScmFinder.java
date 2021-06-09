package com.sap.oss.phosphor.fosstars.tool.github;

import static com.sap.oss.phosphor.fosstars.maven.MavenUtils.readModel;
import static com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject.isOnGitHub;

import com.fasterxml.jackson.databind.JsonNode;
import com.sap.oss.phosphor.fosstars.maven.GAV;
import com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject;
import com.sap.oss.phosphor.fosstars.util.Json;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;

/**
 * The class takes GAV coordinates of an artifact and looks for a URL to its SCM.
 */
public class MavenScmFinder {

  /**
   * A template of a request to the Maven Search API.
   */
  private static final String MAVEN_SEARCH_REQUEST_TEMPLATE
      = "https://search.maven.org/solrsearch/select?q=g:%22{GROUP_ID}%22+AND+a:%22{ARTIFACT_ID}"
      + "%22&core=gav&rows=20&wt=json";

  /**
   * A template of a request for downloading files from the Maven Central repository.
   */
  private static final String MAVEN_DOWNLOAD_REQUEST_TEMPLATE
      = "https://search.maven.org/remotecontent?filepath={PATH}";

  /**
   * A template of a path to a POM file of an artifact in the Maven Central repository.
   */
  private static final String PATH_TEMPLATE
      = "{GROUP}/{ARTIFACT}/{VERSION}/{ARTIFACT}-{VERSION}.pom";

  /**
   * The default charset.
   */
  private static final Charset CHARSET = StandardCharsets.UTF_8;

  /**
   * Takes GAV coordinates of an artifact and looks for a URL to its SCM.
   *
   * @param coordinates The GAV coordinates.
   * @return A URL to SCM.
   * @throws IOException If something went wrong.
   */
  public Optional<String> findScmFor(String coordinates) throws IOException {
    return findScmFor(GAV.parse(coordinates));
  }

  /**
   * Takes GAV coordinates of an artifact and looks for a URL to its SCM.
   *
   * @param gav The GAV coordinates.
   * @return A URL to SCM.
   * @throws IOException If something went wrong.
   */
  public Optional<String> findScmFor(GAV gav) throws IOException {
    Model pom = loadPomFor(gav);
    Scm scm = pom.getScm();
    if (scm == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(scm.getUrl());
  }

  /**
   * Takes GAV coordinates to an artifact and looks for a corresponding GitHub project
   * (maybe a mirror) that contains the source code.
   *
   * @param gav The GAV coordinates.
   * @return A project on GitHub.
   * @throws IOException If something went wrong.
   */
  public Optional<GitHubProject> findGithubProjectFor(String gav) throws IOException {
    Optional<String> scm = findScmFor(gav);
    if (!scm.isPresent()) {
      return Optional.empty();
    }

    String url = scm.get();

    if (isOnGitHub(url)) {
      return Optional.of(GitHubProject.parse(url));
    }

    return tryToGuessGitHubProjectFor(gav);
  }

  /**
   * Takes GAV coordinates and tries to guess a possible GitHub project.
   *
   * @param coordinates The GAV coordinates.
   * @return A project on GitHub if it exists.
   */
  public Optional<GitHubProject> tryToGuessGitHubProjectFor(String coordinates) {
    return tryToGuessGitHubProjectFor(GAV.parse(coordinates));
  }

  /**
   * Takes GAV coordinates and tries to guess a possible GitHub project.
   *
   * @param gav The GAV coordinates.
   * @return A project on GitHub if it exists.
   */
  public Optional<GitHubProject> tryToGuessGitHubProjectFor(GAV gav) {
    Optional<GitHubProject> project = guessGitHubProjectFor(gav);
    if (project.isPresent() && looksLikeValid(project.get())) {
      return project;
    }

    return Optional.empty();
  }

  /**
   * Checks if a specified GitHub project is valid.
   *
   * @param project The project to be checked.
   * @return True if the project is valid, false otherwise.
   */
  private static boolean looksLikeValid(GitHubProject project) {
    try {
      String content = IOUtils.toString(project.scm(), CHARSET);
      return StringUtils.isNotEmpty(content);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Guesses a URL to a GitHub project for specified GAV coordinates.
   *
   * @param gav The GAV coordinates.
   * @return A project on GitHub.
   */
  private static Optional<GitHubProject> guessGitHubProjectFor(GAV gav) {
    Optional<GitHubProject> project = guessApacheProjectFor(gav);
    if (project.isPresent()) {
      return project;
    }

    return guessEclipseProjectFor(gav);
  }

  /**
   * Guesses a URL to a project under Apache organization on GitHub for specified GAV coordinates.
   *
   * @param gav The GAV coordinates.
   * @return A project on GitHub.
   */
  private static Optional<GitHubProject> guessApacheProjectFor(GAV gav) {
    if (!gav.group().startsWith("org.apache")) {
      return Optional.empty();
    }

    return Optional.of(new GitHubProject("apache", gav.artifact()));
  }

  /**
   * Guesses a URL to a project under Eclipse organization on GitHub for specified GAV coordinates.
   *
   * @param gav The GAV coordinates.
   * @return A project on GitHub.
   */
  private static Optional<GitHubProject> guessEclipseProjectFor(GAV gav) {
    if (!gav.group().startsWith("org.eclipse")) {
      return Optional.empty();
    }

    return Optional.of(new GitHubProject("eclipse", gav.artifact()));
  }

  /**
   * Search for the latest version of an artifact in the Maven Central repository.
   *
   * @param gav GAV coordinates of the artifact.
   * @return The latest version of the artifact.
   * @throws IOException If something went wrong.
   */
  private static String latestVersionOf(GAV gav) throws IOException {
    String urlString = MAVEN_SEARCH_REQUEST_TEMPLATE
        .replace("{GROUP_ID}", gav.group())
        .replace("{ARTIFACT_ID}", gav.artifact());
    URL url = new URL(urlString);

    JsonNode node = Json.mapper().readTree(url);

    if (!node.has("response")) {
      throw new IOException("Oh no! The response doesn't have a response filed!");
    }
    JsonNode response = node.get("response");

    if (!response.has("docs")) {
      throw new IOException("Oh no! The response doesn't have a docs field!");
    }
    JsonNode docs = response.get("docs");

    if (!docs.isArray()) {
      throw new IOException("Oh no! Docs element is not an array!");
    }
    Iterator<JsonNode> iterator = docs.iterator();

    if (!iterator.hasNext()) {
      throw new IOException("Oh no! Docs element is empty!");
    }

    JsonNode latest = iterator.next();
    if (!latest.has("v")) {
      throw new IOException("Oh no! The latest element in docs doesn't have a version!");
    }

    return latest.get("v").asText();
  }

  /**
   * Loads a POM file for an artifact.
   *
   * @param gav GAV coordinates of the artifact.
   * @return A Maven model of the POM file.
   * @throws IOException If something wrong.
   */
  private static Model loadPomFor(GAV gav) throws IOException {

    String path = PATH_TEMPLATE
        .replace("{GROUP}", gav.group().replace(".", "/"))
        .replace("{ARTIFACT}", gav.artifact())
        .replace("{VERSION}", gav.version().orElse(latestVersionOf(gav)));
    String urlString = MAVEN_DOWNLOAD_REQUEST_TEMPLATE.replace("{PATH}", path);
    String content = IOUtils.toString(new URL(urlString), CHARSET);

    return readModel(IOUtils.toInputStream(content));
  }

  /**
   * Entry point for demo and testing purposes.
   *
   * @param args Command line options.
   * @throws IOException If something went wrong.
   */
  public static void main(String... args) throws IOException {
    String gav = args.length > 0 ? args[0] : "org.apache.commons:commons-text";
    Optional<GitHubProject> project = new MavenScmFinder().findGithubProjectFor(gav);
    System.out.println(
        project.isPresent() ? String.format("GitHub URL = %s", project.get()) : "No SCM found!");
  }
}
