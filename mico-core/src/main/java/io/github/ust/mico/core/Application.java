package io.github.ust.mico.core;

public class Application extends Service {
    private String deployInformation;
    private String visualizationData;

    //TODO: Verify if all are necessary
    public String getDeployInformation() {
        return deployInformation;
    }

    public void setDeployInformation(String deployInformation) {
        this.deployInformation = deployInformation;
    }

    public String getVisualizationData() {
        return visualizationData;
    }

    public void setVisualizationData(String visualizationData) {
        this.visualizationData = visualizationData;
    }
}