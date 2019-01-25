package io.github.ust.mico.core.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.ust.mico.core.VersionNotSupportedException;
import lombok.*;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import io.swagger.annotations.ApiModelProperty;

/**
 * Represents an application as a set of {@link MicoService}s
 * in the context of MICO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@NodeEntity
public class MicoApplication {

    /**
     * The id of this application.
     */
    @Id
    @GeneratedValue
    private Long id;


    // ----------------------
    // -> Required fields ---
    // ----------------------

    /**
     * A brief name for the application intended
     * for use as a unique identifier.
     */
    @ApiModelProperty(required = true)
    private String shortName;

    /**
     * The name of the artifact. Intended for humans.
     */
    @ApiModelProperty(required = true)
    private String name;

    /**
     * The version of this application.
     */
    @ApiModelProperty(required = true)
    private String version;

    /**
     * Human readable description of this application.
     */
    @ApiModelProperty(required = true)
    private String description;

    /**
     * The services this application is composed of.
     */
    @Singular
    @Relationship(type = "INCLUDES")
    private List<MicoService> services;

    /**
     * The information necessary for deploying this application.
     */
    @ApiModelProperty(required = true)
    private MicoApplicationDeploymentInfo deploymentInfo;


    // ----------------------
    // -> Optional fields ---
    // ----------------------

    /**
     * Human readable contact information for support purposes.
     */
    private String contact;

    /**
     * Human readable information for the application owner
     * who is responsible for this application.
     */
    private String owner;


    @JsonIgnore
    public MicoVersion getMicoVersion() throws VersionNotSupportedException {
        MicoVersion micoVersion = MicoVersion.valueOf(this.version);
        return micoVersion;
    }

}
