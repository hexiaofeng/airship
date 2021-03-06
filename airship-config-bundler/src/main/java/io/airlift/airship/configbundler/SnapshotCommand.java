package io.airlift.airship.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.io.InputSupplier;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.util.FS;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.lang.String.format;

@Command(name = "snapshot", description = "Deploy a snapshot config bundle")
public class SnapshotCommand
        implements Callable<Void>
{
    @Arguments
    public String component;

    @Override
    public Void call()
            throws Exception
    {
        Model model = new Model(Git.wrap(new RepositoryBuilder().findGitDir().setFS(FS.DETECTED).build()));
        Metadata metadata = model.readMetadata();

        String groupId = metadata.getGroupId();
        Preconditions.checkNotNull(groupId, "GroupId missing from metadata file");

        Preconditions.checkState(!model.isDirty(), "Cannot deploy with a dirty working tree");

        Bundle bundle;

        if (component == null) {
            bundle = model.getActiveBundle();
        }
        else {
            bundle = model.getBundle(component);
        }

        Preconditions.checkState(bundle.isSnapshot(), "There are not pending changes for bundle %s. Use released version %s:%s instead",
                bundle.getName(), bundle.getName(), bundle.getVersionString());

        final Map<String, InputSupplier<InputStream>> entries = model.getEntries(bundle);

        if (entries.isEmpty()) {
            throw new RuntimeException("Cannot build an empty config package");
        }

        Maven maven = new Maven(metadata.getSnapshotsRepository(), metadata.getReleasesRepository());
        maven.upload(groupId, bundle.getName(), bundle.getVersionString(), ReleaseCommand.ARTIFACT_TYPE, new ZipGenerator(entries));

        System.out.println(format("Uploaded %s-%s", bundle.getName(), bundle.getVersionString()));

        return null;

    }
}
