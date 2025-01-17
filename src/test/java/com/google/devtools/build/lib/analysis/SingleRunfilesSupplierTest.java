// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.actions.ArtifactRoot.RootType;
import com.google.devtools.build.lib.actions.RunfilesSupplier;
import com.google.devtools.build.lib.actions.RunfilesSupplier.RunfilesTree;
import com.google.devtools.build.lib.actions.util.ActionsTestUtil;
import com.google.devtools.build.lib.analysis.config.BuildConfigurationValue.RunfileSymlinksMode;
import com.google.devtools.build.lib.vfs.DigestHashFunction;
import com.google.devtools.build.lib.vfs.PathFragment;
import com.google.devtools.build.lib.vfs.inmemoryfs.InMemoryFileSystem;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SingleRunfilesSupplier}. */
@RunWith(JUnit4.class)
public final class SingleRunfilesSupplierTest {

  private final ArtifactRoot rootDir =
      ArtifactRoot.asDerivedRoot(
          new InMemoryFileSystem(DigestHashFunction.SHA256).getPath("/"),
          RootType.Output,
          "fake",
          "root",
          "dont",
          "matter");

  @Test
  public void testGetArtifactsWithSingleMapping() {
    List<Artifact> artifacts = mkArtifacts("thing1", "thing2");

    SingleRunfilesSupplier underTest =
        new SingleRunfilesSupplier(
            PathFragment.create("notimportant"),
            mkRunfiles(artifacts),
            /* repoMappingManifest= */ null,
            RunfileSymlinksMode.SKIP,
            /* buildRunfileLinks= */ false);

    assertThat(Iterables.getOnlyElement(underTest.getRunfilesTrees()).getArtifacts().toList())
        .containsExactlyElementsIn(artifacts);
  }

  @Test
  public void withOverriddenRunfilesDir() {
    SingleRunfilesSupplier original =
        new SingleRunfilesSupplier(
            PathFragment.create("old"),
            Runfiles.EMPTY,
            /* repoMappingManifest= */ null,
            RunfileSymlinksMode.SKIP,
            /* buildRunfileLinks= */ false);
    RunfilesTree originalTree = Iterables.getOnlyElement(original.getRunfilesTrees());

    PathFragment newDir = PathFragment.create("new");
    RunfilesSupplier overriddenSupplier = original.withOverriddenRunfilesDir(newDir);
    RunfilesTree overriddenTree = Iterables.getOnlyElement(overriddenSupplier.getRunfilesTrees());

    assertThat(overriddenTree.getExecPath()).isEqualTo(newDir);
    assertThat(overriddenTree.getMapping())
        .isEqualTo(original.getRunfilesTrees().get(0).getMapping());
    assertThat(overriddenTree.getArtifacts()).isEqualTo(originalTree.getArtifacts());
  }

  @Test
  public void withOverriddenRunfilesDir_noChange_sameObject() {
    PathFragment dir = PathFragment.create("dir");
    SingleRunfilesSupplier original =
        new SingleRunfilesSupplier(
            dir,
            Runfiles.EMPTY,
            /* repoMappingManifest= */ null,
            RunfileSymlinksMode.SKIP,
            /* buildRunfileLinks= */ false);
    assertThat(original.withOverriddenRunfilesDir(dir)).isSameInstanceAs(original);
  }

  @Test
  public void cachedMappings() {
    PathFragment dir = PathFragment.create("dir");
    Runfiles runfiles = mkRunfiles(mkArtifacts("a", "b", "c"));
    SingleRunfilesSupplier underTest =
        SingleRunfilesSupplier.createCaching(
            dir,
            runfiles,
            /* repoMappingManifest= */ null,
            RunfileSymlinksMode.SKIP,
            /* buildRunfileLinks= */ false);

    Map<PathFragment, Artifact> mapping1 = underTest.getRunfilesTrees().get(0).getMapping();
    Map<PathFragment, Artifact> mapping2 = underTest.getRunfilesTrees().get(0).getMapping();

    assertThat(mapping1).isEqualTo(runfiles.getRunfilesInputs(null, null, null));
    assertThat(mapping1).isSameInstanceAs(mapping2);
  }

  @Test
  public void cachedMappings_sharedAcrossDirOverrides() {
    PathFragment oldDir = PathFragment.create("old");
    PathFragment newDir = PathFragment.create("new");
    Runfiles runfiles = mkRunfiles(mkArtifacts("a", "b", "c"));
    SingleRunfilesSupplier original =
        SingleRunfilesSupplier.createCaching(
            oldDir,
            runfiles,
            /* repoMappingManifest= */ null,
            RunfileSymlinksMode.SKIP,
            /* buildRunfileLinks= */ false);
    SingleRunfilesSupplier overridden = original.withOverriddenRunfilesDir(newDir);

    Map<PathFragment, Artifact> mappingOld = original.getRunfilesTrees().get(0).getMapping();
    Map<PathFragment, Artifact> mappingNew = overridden.getRunfilesTrees().get(0).getMapping();

    assertThat(mappingOld).isEqualTo(runfiles.getRunfilesInputs(null, null, null));
    assertThat(mappingNew).isEqualTo(runfiles.getRunfilesInputs(null, null, null));
    assertThat(mappingOld.get(newDir)).isSameInstanceAs(mappingNew.get(oldDir));
  }

  private static Runfiles mkRunfiles(Iterable<Artifact> artifacts) {
    return new Runfiles.Builder("TESTING", false).addArtifacts(artifacts).build();
  }

  private ImmutableList<Artifact> mkArtifacts(String... paths) {
    return stream(paths)
        .map(path -> ActionsTestUtil.createArtifact(rootDir, path))
        .collect(toImmutableList());
  }
}
