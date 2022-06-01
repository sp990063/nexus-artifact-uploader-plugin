package sp.sd.nexusartifactuploader;

import java.io.File;

import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.deployment.DeploymentException;
import org.sonatype.aether.impl.ArtifactDescriptorReader;
import org.sonatype.aether.impl.MetadataGeneratorFactory;
import org.sonatype.aether.impl.VersionRangeResolver;
import org.sonatype.aether.impl.VersionResolver;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.LocalRepositoryManager;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;

import hudson.model.TaskListener;

public class ArtifactRepositoryManager {

    private String url;
    private String username;
    private String password;
    private String repo;
    private TaskListener Listener;

    private static final String USER_HOME = System.getProperty("user.home");
    private static final File MAVEN_USER_HOME = new File(USER_HOME, ".m2");

    private RepositorySystem repositorySystem;
    private RepositorySystemSession session;

    public ArtifactRepositoryManager(String url, String username, String password, String repo, TaskListener Listener)
            throws SettingsBuildingException {
        this.url = url;
        this.username = username;
        this.password = password;
        this.repo = repo;
        this.Listener = Listener;
    }

    private RemoteRepository makeRemoteRepository() {
        return new RemoteRepository(this.repo, "default", this.url)
                .setAuthentication(new Authentication(this.username, this.password));
    }

    public void upload(Artifact... artifacts)
            throws DeploymentException, SettingsBuildingException {
        RemoteRepository remoteRepository = makeRemoteRepository();
        DeployRequest deployRequest = new DeployRequest();
        for (Artifact artifact : artifacts) {
            deployRequest.addArtifact(artifact);
        }
        deployRequest.setRepository(remoteRepository);

        final SettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setSystemProperties(System.getProperties());

        Settings settings = new DefaultSettingsBuilderFactory()
                .newInstance()
                .build(request)
                .getEffectiveSettings();

        repositorySystem = new DefaultServiceLocator()
                .addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class)
                .addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class)
                .addService(VersionResolver.class, DefaultVersionResolver.class)
                .addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class)
                .addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class)
                .addService(MetadataGeneratorFactory.class, SnapshotMetadataGeneratorFactory.class)
                .setServices(WagonProvider.class, new ManualWagonProvider())
                .getService(RepositorySystem.class);

        String localRepository = settings.getLocalRepository();
        if (localRepository == null || localRepository.trim().isEmpty()) {
            localRepository = new File(MAVEN_USER_HOME, "repository").getAbsolutePath();
        }

        LocalRepository localRepositoryFromPath = new LocalRepository(localRepository);
        LocalRepositoryManager localRepositoryManager =
                repositorySystem.newLocalRepositoryManager(localRepositoryFromPath);

        session = new MavenRepositorySystemSession()
                .setLocalRepositoryManager(localRepositoryManager)
                .setRepositoryListener(new RepositoryListener())
                .setTransferListener(new sp.sd.nexusartifactuploader.TransferListener(Listener));
        repositorySystem.deploy(session, deployRequest);
    }
}

