package com.sap.oss.phosphor.fosstars.tool.github;

import static com.sap.oss.phosphor.fosstars.model.rating.oss.OssRulesOfPlayRating.OssRulesOfPlayLabel.FAILED;
import static com.sap.oss.phosphor.fosstars.model.rating.oss.OssRulesOfPlayRating.OssRulesOfPlayLabel.PASSED;
import static com.sap.oss.phosphor.fosstars.model.rating.oss.OssRulesOfPlayRating.OssRulesOfPlayLabel.PASSED_WITH_WARNING;
import static com.sap.oss.phosphor.fosstars.model.rating.oss.OssRulesOfPlayRating.OssRulesOfPlayLabel.UNCLEAR;
import static com.sap.oss.phosphor.fosstars.model.score.oss.OssRulesOfPlayScore.SCORE_WITH_WARNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sap.oss.phosphor.fosstars.advice.oss.OssRulesOfPlayAdvisor;
import com.sap.oss.phosphor.fosstars.model.Confidence;
import com.sap.oss.phosphor.fosstars.model.Label;
import com.sap.oss.phosphor.fosstars.model.RatingRepository;
import com.sap.oss.phosphor.fosstars.model.Score;
import com.sap.oss.phosphor.fosstars.model.rating.oss.OssRulesOfPlayRating;
import com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject;
import com.sap.oss.phosphor.fosstars.model.value.RatingValue;
import com.sap.oss.phosphor.fosstars.model.value.ScoreValue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class OssRulesOfPlayMarkdownReporterTest {

  @Test
  public void testReport() throws IOException {
    Path outputDirectory = Files.createTempDirectory(
        OssRulesOfPlayMarkdownReporterTest.class.getName());
    try {
      OssRulesOfPlayRating rating = RatingRepository.INSTANCE.rating(OssRulesOfPlayRating.class);

      GitHubProject passedProject = new GitHubProject("org", "passed");
      passedProject.set(
          new RatingValue(
              new ScoreValue(rating.score()).set(Score.MAX).confidence(10.0),
              PASSED));

      GitHubProject projectWithWarnings = new GitHubProject("org", "warnings");
      projectWithWarnings.set(
          new RatingValue(
              new ScoreValue(rating.score()).set(SCORE_WITH_WARNING).confidence(9.0),
              PASSED_WITH_WARNING));

      GitHubProject failedProject = new GitHubProject("org", "failed");
      failedProject.set(
          new RatingValue(
              new ScoreValue(rating.score()).set(Score.MIN).confidence(8.0),
              FAILED));

      GitHubProject unclearProject = new GitHubProject("org", "unclear");
      unclearProject.set(
          new RatingValue(
              new ScoreValue(rating.score()).set(Score.MIN).confidence(Confidence.MIN),
              UNCLEAR));

      List<GitHubProject> projects = Arrays.asList(
          passedProject, projectWithWarnings, failedProject, unclearProject
      );

      OssRulesOfPlayMarkdownReporter reporter
          = new OssRulesOfPlayMarkdownReporter(
              outputDirectory.toString(), new OssRulesOfPlayAdvisor());
      reporter.runFor(projects);

      Path reportFileName = outputDirectory.resolve(OssRulesOfPlayMarkdownReporter.REPORT_FILENAME);
      assertTrue(Files.exists(reportFileName));

      String report = new String(Files.readAllBytes(reportFileName));
      System.out.println(report);

      assertFalse(report.isEmpty());
      assertTrue(report.contains("Total"));
      for (Label label : OssRulesOfPlayRating.OssRulesOfPlayLabel.values()) {
        assertTrue(report.contains(label.name()));
      }
      assertTrue(report.contains("org/passed"));
      assertTrue(report.contains("org/warnings"));
      assertTrue(report.contains("org/failed"));
      assertTrue(report.contains("org/unclear"));
      assertEquals(1, linesWith("100%", report));
      assertEquals(4, linesWith("25.0%", report));
    } finally {
      FileUtils.forceDeleteOnExit(outputDirectory.toFile());
    }
  }

  private static int linesWith(String string, String content) throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(content));

    String line;
    int n = 0;
    while ((line = reader.readLine()) != null) {
      if (line.contains(string)) {
        n++;
      }
    }

    return n;
  }
}