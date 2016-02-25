package org.carlspring.strongbox.artifact.generator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Plugin;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.project.artifact.PluginArtifact;
import org.carlspring.maven.commons.util.ArtifactUtils;
import org.carlspring.strongbox.client.ArtifactClient;
import org.carlspring.strongbox.client.ArtifactOperationException;
import org.carlspring.strongbox.client.ArtifactTransportException;
import org.carlspring.strongbox.io.ArtifactInputStream;
import org.carlspring.strongbox.matadata.util.MetadataMerger;
import org.carlspring.strongbox.security.encryption.EncryptionAlgorithmsEnum;
import org.carlspring.strongbox.storage.metadata.MetadataHelper;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * @author mtodorov
 */
public class ArtifactDeployer extends ArtifactGenerator
{

    private String username;

    private String password;

    private ArtifactClient client;

    private MetadataMerger metadataMerger;

    public ArtifactDeployer()
    {
    }

    public ArtifactDeployer(String basedir)
    {
        super(basedir);
    }

    public ArtifactDeployer(File basedir)
    {
        super(basedir);
    }

    public void initializeClient()
    {
        client = ArtifactClient.getTestInstance();
    }

    public void generateAndDeployArtifact(Artifact artifact, String storageId, String repositoryId)
            throws NoSuchAlgorithmException, XmlPullParserException, IOException, ArtifactOperationException
    {
        generateAndDeployArtifact(artifact, null, storageId, repositoryId, "jar");
    }

    public void generateAndDeployArtifact(Artifact artifact, String[] classifiers, String storageId,
            String repositoryId, String packaging)
                    throws NoSuchAlgorithmException, XmlPullParserException, IOException, ArtifactOperationException
    {
        if (client == null)
        {
            initializeClient();
        }

        generatePom(artifact, packaging);
        createArchive(artifact);

        deploy(artifact, storageId, repositoryId);
        deployPOM(ArtifactUtils.getPOMArtifact(artifact), storageId, repositoryId);

        if (classifiers != null)
        {
            for (String classifier : classifiers)
            {
                // We're assuming the type of the classifier is the same as the
                // one of the main artifact
                Artifact artifactWithClassifier = ArtifactUtils
                        .getArtifactFromGAVTC(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
                                + artifact.getVersion() + ":" + artifact.getType() + ":" + classifier);
                generate(artifactWithClassifier);

                deploy(artifactWithClassifier, storageId, repositoryId);
            }
        }

        // TODO: SB-230: Implement metadata merging for the ArtifactDeployer
        // TODO: Update the metadata file on the repositoryId's side.

        mergeMetada(artifact);
    }

    private void mergeMetada(Artifact artifact)
    {
        if (metadataMerger == null)
        {
            metadataMerger = new MetadataMerger();
        }
        if (artifact.isSnapshot())
        {
            metadataMerger.updateMetadataAtVersionLevel(artifact, client);
        }
        metadataMerger.updateMetadataAtArtifactLevel(artifact, client);
        if (artifact instanceof PluginArtifact)
        {
            metadataMerger.updateMetadataAtPluginLevel((PluginArtifact)artifact, client);
        }
    }

    public void deploy(Artifact artifact, String storageId, String repositoryId)
            throws ArtifactOperationException, IOException, NoSuchAlgorithmException, XmlPullParserException
    {
        File artifactFile = new File(getBasedir(), ArtifactUtils.convertArtifactToPath(artifact));
        ArtifactInputStream ais = new ArtifactInputStream(artifact, new FileInputStream(artifactFile));

        client.addArtifact(artifact, storageId, repositoryId, ais);

        deployChecksum(ais, storageId, repositoryId, artifact);
    }

    private void deployChecksum(ArtifactInputStream ais, String storageId, String repositoryId, Artifact artifact)
            throws ArtifactOperationException, IOException
    {
        for (Map.Entry entry : ais.getHexDigests().entrySet())
        {
            final String algorithm = (String) entry.getKey();
            final String checksum = (String) entry.getValue();

            ByteArrayInputStream bais = new ByteArrayInputStream(checksum.getBytes());

            final String extensionForAlgorithm = EncryptionAlgorithmsEnum.fromAlgorithm(algorithm).getExtension();

            String artifactToPath = ArtifactUtils.convertArtifactToPath(artifact) + extensionForAlgorithm;
            String url = client.getContextBaseUrl() + "/storages/" + storageId + "/" + repositoryId + "/"
                    + artifactToPath;
            String artifactFileName = ais.getArtifactFileName() + extensionForAlgorithm;

            client.deployFile(bais, url, artifactFileName);
        }
    }

    private void deployPOM(Artifact artifact, String storageId, String repositoryId)
            throws NoSuchAlgorithmException, IOException, ArtifactOperationException
    {
        File pomFile = new File(getBasedir(), ArtifactUtils.convertArtifactToPath(artifact));

        InputStream is = new FileInputStream(pomFile);
        ArtifactInputStream ais = new ArtifactInputStream(artifact, is);

        client.addArtifact(artifact, storageId, repositoryId, ais);

        deployChecksum(ais, storageId, repositoryId, artifact);
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public ArtifactClient getClient()
    {
        return client;
    }

    public void setClient(ArtifactClient client)
    {
        this.client = client;
    }

}
