package com.sap.oss.phosphor.fosstars.data.github;

import static com.sap.oss.phosphor.fosstars.model.feature.oss.OssFeatures.HAS_README;
import static com.sap.oss.phosphor.fosstars.model.feature.oss.OssFeatures.INCOMPLETE_README;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.oss.phosphor.fosstars.data.NoValueCache;
import com.sap.oss.phosphor.fosstars.model.Feature;
import com.sap.oss.phosphor.fosstars.model.Value;
import com.sap.oss.phosphor.fosstars.model.ValueSet;
import com.sap.oss.phosphor.fosstars.model.subject.oss.GitHubProject;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public class ReadmeInfoTest extends TestGitHubDataFetcherHolder {

  @Test
  public void testSupportedFeatures() {
    Set<Feature<?>> features =  new ReadmeInfo(fetcher).supportedFeatures();
    assertEquals(2, features.size());
    assertTrue(features.contains(HAS_README));
    assertTrue(features.contains(INCOMPLETE_README));
  }

  @Test
  public void testWithReadme() throws IOException {
    GitHubProject project = new GitHubProject("test", "project");
    LocalRepository localRepository = mock(LocalRepository.class);
    when(localRepository.hasFile("README")).thenReturn(true);
    TestGitHubDataFetcher.addForTesting(project, localRepository);

    ReadmeInfo provider = new ReadmeInfo(fetcher);
    provider.requiredContentPatterns("# Mandatory header", "^((?!Prohibited phrase).)*$");
    provider.set(NoValueCache.create());

    when(localRepository.readTextFrom("README"))
        .thenReturn(Optional.of(String.join("\n",
            "This is README",
            "",
            "# Mandatory header",
            "",
            "Don't trouble trouble till trouble troubles you."
        )));
    ValueSet values = provider.fetchValuesFor(project);
    Value<Boolean> value = checkValue(values, HAS_README, true);
    assertTrue(value.explanation().isEmpty());
    value = checkValue(values, INCOMPLETE_README, false);
    assertTrue(value.explanation().isEmpty());

    when(localRepository.readTextFrom("README"))
        .thenReturn(Optional.of(String.join("\n",
            "This is README",
            "",
            "# Another header"
        )));
    values = provider.fetchValuesFor(project);
    value = checkValue(values, HAS_README, true);
    assertTrue(value.explanation().isEmpty());
    value = checkValue(values, INCOMPLETE_README, true);
    assertFalse(value.explanation().isEmpty());
    assertTrue(value.explanation().get(0).contains("Mandatory header"));

    when(localRepository.readTextFrom("README"))
        .thenReturn(Optional.of(String.join("\n",
            "This is README",
            "",
            "# Mandatory header",
            "",
            "Prohibited phrase",
            ""
        )));
    values = provider.fetchValuesFor(project);
    value = checkValue(values, HAS_README, true);
    assertTrue(value.explanation().isEmpty());
    value = checkValue(values, INCOMPLETE_README, true);
    assertFalse(value.explanation().isEmpty());
    assertTrue(value.explanation().get(0).contains("Prohibited phrase"));
  }

  @Test
  public void testWithoutReadme() throws IOException {
    GitHubProject project = new GitHubProject("test", "project");
    LocalRepository localRepository = mock(LocalRepository.class);
    when(localRepository.hasFile(any())).thenReturn(false);
    TestGitHubDataFetcher.addForTesting(project, localRepository);

    ReadmeInfo provider = new ReadmeInfo(fetcher);
    ValueSet values = provider.fetchValuesFor(project);
    Value<Boolean> value = checkValue(values, HAS_README, false);
    assertFalse(value.explanation().isEmpty());
    value = checkValue(values, INCOMPLETE_README, true);
    assertFalse(value.explanation().isEmpty());
  }

  private static Value<Boolean> checkValue(
      ValueSet values, Feature<Boolean> feature, boolean expected) {

    Optional<Value<Boolean>> something = values.of(feature);
    assertTrue(something.isPresent());
    Value<Boolean> value = something.get();
    assertEquals(expected, value.get());
    return value;
  }
}