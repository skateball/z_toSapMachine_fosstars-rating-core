package com.sap.sgs.phosphor.fosstars.advice.oss;

import static com.sap.sgs.phosphor.fosstars.advice.oss.AbstractOssAdvisor.OssAdviceContextFactory.WITH_EMPTY_CONTEXT;
import static com.sap.sgs.phosphor.fosstars.model.feature.oss.OssFeatures.LANGUAGES;
import static com.sap.sgs.phosphor.fosstars.model.feature.oss.OssFeatures.USES_ADDRESS_SANITIZER;
import static com.sap.sgs.phosphor.fosstars.model.feature.oss.OssFeatures.USES_MEMORY_SANITIZER;
import static com.sap.sgs.phosphor.fosstars.model.feature.oss.OssFeatures.USES_UNDEFINED_BEHAVIOR_SANITIZER;
import static com.sap.sgs.phosphor.fosstars.model.other.Utils.allUnknown;
import static com.sap.sgs.phosphor.fosstars.model.value.Language.JAVA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sap.sgs.phosphor.fosstars.model.Rating;
import com.sap.sgs.phosphor.fosstars.model.RatingRepository;
import com.sap.sgs.phosphor.fosstars.model.ValueSet;
import com.sap.sgs.phosphor.fosstars.model.rating.oss.OssSecurityRating;
import com.sap.sgs.phosphor.fosstars.model.subject.oss.GitHubProject;
import com.sap.sgs.phosphor.fosstars.model.value.Languages;
import com.sap.sgs.phosphor.fosstars.model.value.ValueHashSet;
import org.junit.Test;

public class MemorySafetyAdvisorTest {

  @Test
  public void testAdvicesForMemorySanitizers() {
    MemorySafetyAdvisor advisor = new MemorySafetyAdvisor(WITH_EMPTY_CONTEXT);
    GitHubProject project = new GitHubProject("org", "test");

    // no advices if no rating value is set
    assertTrue(advisor.adviseFor(project).isEmpty());

    Rating rating = RatingRepository.INSTANCE.rating(OssSecurityRating.class);
    ValueSet values = new ValueHashSet();

    // no advices for an unknown values
    values.update(allUnknown(rating.score().allFeatures()));
    assertTrue(advisor.adviseFor(project).isEmpty());

    // no advices if sanitizers are enabled
    values.update(USES_ADDRESS_SANITIZER.value(true));
    values.update(USES_MEMORY_SANITIZER.value(true));
    values.update(USES_UNDEFINED_BEHAVIOR_SANITIZER.value(true));
    project.set(rating.calculate(values));
    assertTrue(advisor.adviseFor(project).isEmpty());

    // expect an advice if AddressSanitizer is not used
    values.update(USES_ADDRESS_SANITIZER.value(false));
    project.set(rating.calculate(values));
    assertEquals(1, advisor.adviseFor(project).size());

    // expect an advice if MemorySanitizer is not used
    values.update(USES_MEMORY_SANITIZER.value(false));
    project.set(rating.calculate(values));
    assertEquals(2, advisor.adviseFor(project).size());

    // expect an advice if UndefinedBehaviorSanitizer is not used
    values.update(USES_UNDEFINED_BEHAVIOR_SANITIZER.value(false));
    project.set(rating.calculate(values));
    assertEquals(3, advisor.adviseFor(project).size());
  }

  @Test
  public void testAdvicesWhenMemorySafetyTestingScoreIsNotApplicable() {
    final MemorySafetyAdvisor advisor = new MemorySafetyAdvisor(WITH_EMPTY_CONTEXT);
    final GitHubProject project = new GitHubProject("org", "test");

    Rating rating = RatingRepository.INSTANCE.rating(OssSecurityRating.class);
    ValueSet values = new ValueHashSet();
    values.update(allUnknown(rating.score().allFeatures()));
    values.update(USES_ADDRESS_SANITIZER.value(false));
    values.update(USES_MEMORY_SANITIZER.value(false));
    values.update(USES_UNDEFINED_BEHAVIOR_SANITIZER.value(false));
    values.update(LANGUAGES.value(Languages.of(JAVA)));
    project.set(rating.calculate(values));

    // sanitizers are not applicable for projects that use only Java
    // therefore no advices are expected.
    assertTrue(advisor.adviseFor(project).isEmpty());
  }
}