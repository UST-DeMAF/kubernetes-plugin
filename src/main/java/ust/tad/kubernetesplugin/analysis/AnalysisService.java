package ust.tad.kubernetesplugin.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ust.tad.kubernetesplugin.analysistask.AnalysisTaskResponseSender;
import ust.tad.kubernetesplugin.analysistask.Location;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.Container;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.ContainerPort;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.EnvironmentVariable;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.KubernetesDeployment;
import ust.tad.kubernetesplugin.kubernetesmodel.deployment.Label;
import ust.tad.kubernetesplugin.kubernetesmodel.service.KubernetesService;
import ust.tad.kubernetesplugin.kubernetesmodel.service.Selector;
import ust.tad.kubernetesplugin.kubernetesmodel.service.ServicePort;
import ust.tad.kubernetesplugin.models.ModelsService;
import ust.tad.kubernetesplugin.models.tadm.InvalidPropertyValueException;
import ust.tad.kubernetesplugin.models.tadm.InvalidRelationException;
import ust.tad.kubernetesplugin.models.tadm.TechnologyAgnosticDeploymentModel;
import ust.tad.kubernetesplugin.models.tsdm.DeploymentModelContent;
import ust.tad.kubernetesplugin.models.tsdm.InvalidAnnotationException;
import ust.tad.kubernetesplugin.models.tsdm.InvalidNumberOfContentException;
import ust.tad.kubernetesplugin.models.tsdm.InvalidNumberOfLinesException;
import ust.tad.kubernetesplugin.models.tsdm.Line;
import ust.tad.kubernetesplugin.models.tsdm.TechnologySpecificDeploymentModel;

@Service
public class AnalysisService {

    private static final Logger LOG =
      LoggerFactory.getLogger(AnalysisService.class);
    
    @Autowired
    private ModelsService modelsService;

    @Autowired
    private AnalysisTaskResponseSender analysisTaskResponseSender;

    @Autowired
    private TransformationService transformationService;

    private static final Set<String> supportedFileExtensions = Set.of("yaml", "yml");
    
    private TechnologySpecificDeploymentModel tsdm;

    private TechnologyAgnosticDeploymentModel tadm;

    private Set<Integer> newEmbeddedDeploymentModelIndexes = new HashSet<>();

    private Set<KubernetesDeployment> deployments = new HashSet<>();

    private Set<KubernetesService> services = new HashSet<>();

    /**
     * Start the analysis of the deployment model.
     * 1. Retrieve internal deployment models from models service
     * 2. Parse in technology-specific deployment model from locations
     * 3. Update tsdm with new information
     * 4. Transform to EDMM entities and update tadm
     * 5. Send updated models to models service
     * 6. Send AnalysisTaskResponse or EmbeddedDeploymentModelAnalysisRequests if present 
     * 
     * @param taskId
     * @param transformationProcessId
     * @param commands
     * @param locations
     */
    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        TechnologySpecificDeploymentModel completeTsdm = modelsService.getTechnologySpecificDeploymentModel(transformationProcessId);
        this.tsdm = getExistingTsdm(completeTsdm, locations);
        if(tsdm == null) {
            analysisTaskResponseSender.sendFailureResponse(taskId, "No technology-specific deployment model found!");
            return;            
        }
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        try {
            runAnalysis(locations);
        } catch (URISyntaxException | IOException | InvalidNumberOfLinesException | InvalidAnnotationException | InvalidNumberOfContentException | InvalidPropertyValueException | InvalidRelationException e) { 
            e.printStackTrace();
            analysisTaskResponseSender.sendFailureResponse(taskId, e.getClass()+": "+e.getMessage());
            return;
        }

        updateDeploymentModels(this.tsdm, this.tadm);

        if(newEmbeddedDeploymentModelIndexes.isEmpty()) {
            analysisTaskResponseSender.sendSuccessResponse(taskId);
        } else {
            for (int index : newEmbeddedDeploymentModelIndexes) {
                analysisTaskResponseSender.sendEmbeddedDeploymentModelAnalysisRequestFromModel(
                    this.tsdm.getEmbeddedDeploymentModels().get(index), taskId); 
            }
            analysisTaskResponseSender.sendSuccessResponse(taskId);
        }
    }

    private TechnologySpecificDeploymentModel getExistingTsdm(TechnologySpecificDeploymentModel tsdm, List<Location> locations) {
        for (DeploymentModelContent content : tsdm.getContent()) {
            for (Location location : locations) {
                if (location.getUrl().equals(content.getLocation())) {
                    return tsdm;
                }
            }
        }
        for (TechnologySpecificDeploymentModel embeddedDeploymentModel : tsdm.getEmbeddedDeploymentModels()) {
            TechnologySpecificDeploymentModel foundModel =  getExistingTsdm(embeddedDeploymentModel, locations);
            if (foundModel != null) {
                return foundModel;
            }
        }
        return null;
    }
    
    private void updateDeploymentModels(TechnologySpecificDeploymentModel tsdm, TechnologyAgnosticDeploymentModel tadm) {
        modelsService.updateTechnologySpecificDeploymentModel(tsdm);
        modelsService.updateTechnologyAgnosticDeploymentModel(tadm);
    }

    /**
     * Iterate over the locations and parse in all files that can be found.
     * If the URL ends with a ".", remove it.
     * The file has to have a fileextension contained in the supported fileextension Set, otherwise it will be ignored.
     * If the given location is a directory, iterate over all contained files.
     * Removes the deployment model content associated with the old directory locations
     * because it has been resolved to the contained files.
     * 
     * @param locations
     * @throws InvalidNumberOfContentException
     * @throws InvalidAnnotationException
     * @throws InvalidNumberOfLinesException
     * @throws IOException
     * @throws URISyntaxException
     * @throws InvalidPropertyValueException
     * @throws InvalidRelationException
     */
    private void runAnalysis(List<Location> locations) throws URISyntaxException, IOException, InvalidNumberOfLinesException, InvalidAnnotationException, InvalidNumberOfContentException, InvalidPropertyValueException, InvalidRelationException {
        for(Location location : locations) {
            String locationURLString = location.getUrl().toString().trim().replaceAll("\\.$", "");
            URL locationURL = new URL(locationURLString);

            if ("file".equals(locationURL.getProtocol()) && new File(locationURL.toURI()).isDirectory()) {
                File directory = new File(locationURL.toURI());
                for (File file : directory.listFiles()) {
                    String fileExtension = StringUtils.getFilenameExtension(file.toURI().toURL().toString());
                    if(fileExtension != null && supportedFileExtensions.contains(fileExtension)) {                        
                        parseFile(file.toURI().toURL());
                    }
                }
                DeploymentModelContent contentToRemove = new DeploymentModelContent();
                for (DeploymentModelContent content : this.tsdm.getContent()) {
                    if (content.getLocation().equals(location.getUrl())) {
                        contentToRemove = content;
                    }
                }
                this.tsdm.removeDeploymentModelContent(contentToRemove);
            } else {
                String fileExtension = StringUtils.getFilenameExtension(locationURLString);
                if(supportedFileExtensions.contains(fileExtension)) {  
                    parseFile(locationURL);
                }
            }
        }
        this.tadm = transformationService.transformInternalToTADM(this.tadm, this.deployments, this.services);
    }

    public void parseFile(URL url) throws IOException, InvalidNumberOfLinesException, InvalidAnnotationException {
        DeploymentModelContent deploymentModelContent = new DeploymentModelContent();
        deploymentModelContent.setLocation(url);

        List<Line> lines = new ArrayList<>();
        int lineNumber = 1;
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        while(reader.ready()) {
            String nextline = reader.readLine();
            if (nextline.startsWith("kind:")) {
                String kind = nextline.split("kind:")[1].trim();
                kind = kind.split("#")[0].trim();
                List<String> readInLines = new ArrayList<>();
                int startLineNumber = lineNumber;
                while (reader.ready() && !nextline.equals("---")) {
                    readInLines.add(nextline);
                    nextline = reader.readLine();
                    lineNumber++;
                }
                switch (kind) {                    
                    case "Service":                        
                        lines.addAll(createService(startLineNumber, readInLines));
                        break;
                    case "StatefulSet":
                    case "Deployment":
                        lines.addAll(createDeployment(startLineNumber, readInLines));
                        break;               
                    default:
                        lines.addAll(createLinesForUnknownType(lineNumber, readInLines));
                        break;
                }
            }
            lineNumber++;
        }
        reader.close();

        if(!lines.isEmpty()) {
            deploymentModelContent.setLines(lines);
            this.tsdm.addDeploymentModelContent(deploymentModelContent);
        }
    }

    private List<Line> createLinesForUnknownType(int lineNumber, List<String> readInLines) throws InvalidAnnotationException {
        List<Line> lines = new ArrayList<>();
        for (int i = lineNumber; i <= readInLines.size()+lineNumber; i++) {
            lines.add(new Line(i, 0D, true));
        }
        return lines;
    }

    private List<Line> createService(int lineNumber, List<String> readInLines) throws InvalidAnnotationException {
        List<Line> lines = new ArrayList<>();
        KubernetesService kubernetesService = new KubernetesService();
        ListIterator<String> linesIterator = readInLines.listIterator();

        while (linesIterator.hasNext()) {
            String currentLine = linesIterator.next();
            if (currentLine.startsWith("metadata:")) {
                lines.add(new Line(lineNumber, 1D, true));
                while (linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("  ")) {
                    lineNumber++;
                    if (currentLine.trim().startsWith("labels:")) {
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && linesIterator.next().startsWith("    ")) {
                            lineNumber++;
                            lines.add(new Line(lineNumber, 1D, true));
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else if (currentLine.trim().startsWith("name:")) {
                        String name = currentLine.split("name:")[1].trim();
                        kubernetesService.setName(name);
                        lines.add(new Line(lineNumber, 1D, true));
                    } else if (currentLine.trim().startsWith("#")) {
                        continue;
                    }
                    else {
                        lines.add(new Line(lineNumber, 0D, true));
                    }
                }
                if (linesIterator.hasNext()) {
                    linesIterator.previous();                                
                }
            } else if (currentLine.startsWith("spec:")) {
                lines.add(new Line(lineNumber, 1D, true));
                while (linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("  ")) {
                    lineNumber++;
                    if (currentLine.trim().startsWith("ports:")) { 
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && (currentLine = linesIterator.next()).matches("^\\s*-.*")) {          
                            ServicePort servicePort = new ServicePort();
                            currentLine = currentLine.replaceFirst("-", " ");
                            int numberOfWhitespaces = currentLine.length() - currentLine.stripLeading().length();
                            String leadingWhiteSpaces = currentLine.substring(0, numberOfWhitespaces);
                            while(currentLine.startsWith(leadingWhiteSpaces)) {
                                lineNumber++;
                                String[] lineSplit = currentLine.split(":");
                                if (currentLine.trim().startsWith("name:")) {
                                    lines.add(new Line(lineNumber, 1D, true));
                                    servicePort.setName(lineSplit[1].trim());
                                } else if (currentLine.trim().startsWith("port:")) {
                                    lines.add(new Line(lineNumber, 1D, true));
                                    servicePort.setPort(Integer.parseInt(lineSplit[1].trim()));
                                } else if (currentLine.trim().startsWith("targetPort:")) {
                                    lines.add(new Line(lineNumber, 1D, true));
                                    servicePort.setTargetPort(Integer.parseInt(lineSplit[1].trim()));                                    
                                } else {
                                    lines.add(new Line(lineNumber, 0D, true));
                                }

                                if (linesIterator.hasNext()) {
                                    currentLine = linesIterator.next();
                                } else {
                                    break;
                                }
                            }
                            if (linesIterator.hasNext()) {
                                linesIterator.previous();                                
                            }
                            Set<ServicePort> servicePorts = kubernetesService.getServicePorts();
                            servicePorts.add(servicePort);
                            kubernetesService.setServicePorts(servicePorts);
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else if (currentLine.trim().startsWith("selector:")) {
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("    ")) {
                            lineNumber++;
                            String[] lineSplit = currentLine.split(":");
                            Selector selector = new Selector(lineSplit[0].trim(), lineSplit[1].trim());
                            Set<Selector> selectors = kubernetesService.getSelectors();
                            selectors.add(selector);
                            kubernetesService.setSelectors(selectors);
                            lines.add(new Line(lineNumber, 1D, true));
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else {
                        lines.add(new Line(lineNumber, 0D, true));
                    }
                }
                if (linesIterator.hasNext()) {
                    linesIterator.previous();                                
                }
            } else if (currentLine.startsWith("kind:")) {
                lines.add(new Line(lineNumber, 1D, true));
            } else {
                lines.add(new Line(lineNumber, 0D, true));
            }
            lineNumber++;
        }
        this.services.add(kubernetesService);
        return lines;
    }

    private List<Line> createDeployment(int lineNumber, List<String> readInLines) throws InvalidAnnotationException {
        List<Line> lines = new ArrayList<>();
        KubernetesDeployment kubernetesDeployment  = new KubernetesDeployment();
        ListIterator<String> linesIterator = readInLines.listIterator();

        while (linesIterator.hasNext()) {
            String currentLine = linesIterator.next();
            if (currentLine.startsWith("metadata:")) {
                lines.add(new Line(lineNumber, 1D, true));
                while (linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("  ")) {
                    lineNumber++;
                    if (currentLine.trim().startsWith("labels:")) {
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("    ")) {
                            lineNumber++;
                            String[] lineSplit = currentLine.split(":");
                            Label label = new Label(lineSplit[0].trim(), lineSplit[1].trim());
                            Set<Label> labels = kubernetesDeployment.getLabels();
                            labels.add(label);
                            kubernetesDeployment.setLabels(labels);
                            lines.add(new Line(lineNumber, 1D, true));
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else if (currentLine.trim().startsWith("name:")) {
                        String name = currentLine.split("name:")[1].trim();
                        kubernetesDeployment.setName(name);
                        lines.add(new Line(lineNumber, 1D, true));
                    } else if (currentLine.trim().startsWith("#")) {
                        continue;
                    }
                    else {
                        lines.add(new Line(lineNumber, 0D, true));
                    }
                }
                if (linesIterator.hasNext()) {
                    linesIterator.previous();                                
                }
            } else if (currentLine.startsWith("spec:")) {
                lines.add(new Line(lineNumber, 1D, true));
                while (linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("  ")) {
                    lineNumber++;                    
                    if (currentLine.trim().startsWith("replicas:")) { 
                        lines.add(new Line(lineNumber, 1D, true));
                        int replicas = Integer.parseInt(currentLine.split("replicas:")[1].trim());
                        kubernetesDeployment.setReplicas(replicas);
                    } else if (currentLine.trim().startsWith("selector:")) {
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && linesIterator.next().startsWith("    ")) {
                            lineNumber++;
                            lines.add(new Line(lineNumber, 1D, true));
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else if (currentLine.trim().startsWith("template:")) {
                        lines.add(new Line(lineNumber, 1D, true));
                        while(linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("    ")) {
                            lineNumber++;
                            if (currentLine.trim().startsWith("metadata:")) {
                                lines.add(new Line(lineNumber, 1D, true));
                                while(linesIterator.hasNext() && linesIterator.next().startsWith("      ")) {
                                    lineNumber++;
                                    lines.add(new Line(lineNumber, 1D, true));
                                }
                                if (linesIterator.hasNext()) {
                                    linesIterator.previous();                                
                                }
                            } else if (currentLine.trim().startsWith("spec:")) {
                                lines.add(new Line(lineNumber, 1D, true));
                                while(linesIterator.hasNext() && (currentLine = linesIterator.next()).startsWith("      ")) {
                                    lineNumber++;
                                    if (currentLine.trim().startsWith("containers:")) {
                                        lines.add(new Line(lineNumber, 1D, true));
                                        while(linesIterator.hasNext() && (currentLine = linesIterator.next()).matches("^\\s*-.*")) {          
                                            Container container = new Container();
                                            currentLine = currentLine.replaceFirst("-", " ");
                                            int numberOfWhitespaces = currentLine.length() - currentLine.stripLeading().length();
                                            String leadingWhiteSpaces = currentLine.substring(0, numberOfWhitespaces);
                                            while(currentLine.startsWith(leadingWhiteSpaces)) {
                                                lineNumber++;
                                                if (currentLine.trim().startsWith("name:")) {
                                                    lines.add(new Line(lineNumber, 1D, true));
                                                    container.setName(currentLine.split("name:")[1].trim());
                                                } else if (currentLine.trim().startsWith("image:")) {
                                                    lines.add(new Line(lineNumber, 1D, true));
                                                    container.setImage(currentLine.split("image:")[1].trim());
                                                } else if (currentLine.trim().startsWith("ports:")) {
                                                    lines.add(new Line(lineNumber, 1D, true));
                                                    Set<ContainerPort> containerPorts = new HashSet<>();
                                                    while(linesIterator.hasNext() && (currentLine = linesIterator.next()).matches("^\\s*-.*")) {          
                                                        ContainerPort containerPort = new ContainerPort();
                                                        currentLine = currentLine.replaceFirst("-", " ");
                                                        int numberOfWhitespacesPort = currentLine.length() - currentLine.stripLeading().length();
                                                        String leadingWhiteSpacesPort = currentLine.substring(0, numberOfWhitespacesPort);
                                                        while(currentLine.startsWith(leadingWhiteSpacesPort)) {
                                                            lineNumber++;
                                                            String[] lineSplitPort = currentLine.split(":");
                                                            if (currentLine.trim().startsWith("name:")) {
                                                                lines.add(new Line(lineNumber, 1D, true));
                                                                containerPort.setName(lineSplitPort[1].trim());
                                                            } else if (currentLine.trim().startsWith("containerPort:")) {
                                                                lines.add(new Line(lineNumber, 1D, true));
                                                                containerPort.setPort(Integer.parseInt(lineSplitPort[1].trim()));
                                                            } else {
                                                                lines.add(new Line(lineNumber, 0D, true));
                                                            }                            
                                                            if (linesIterator.hasNext()) {
                                                                currentLine = linesIterator.next();
                                                            } else {
                                                                break;
                                                            }
                                                        }
                                                        if (linesIterator.hasNext()) {
                                                            linesIterator.previous();                                
                                                        }
                                                        containerPorts.add(containerPort);
                                                    }
                                                    if (linesIterator.hasNext()) {
                                                        linesIterator.previous();                                
                                                    }
                                                    container.setContainerPorts(containerPorts);                                    
                                                } else if (currentLine.trim().startsWith("env:")) {
                                                    lines.add(new Line(lineNumber, 1D, true));
                                                    Set<EnvironmentVariable> environmentVariables = new HashSet<>();
                                                    while(linesIterator.hasNext() && (currentLine = linesIterator.next()).matches("^\\s*-.*")) {          
                                                        EnvironmentVariable environmentVariable = new EnvironmentVariable();
                                                        currentLine = currentLine.replaceFirst("-", " ");
                                                        int numberOfWhitespacesEnv = currentLine.length() - currentLine.stripLeading().length();
                                                        String leadingWhiteSpacesEnv = currentLine.substring(0, numberOfWhitespacesEnv);
                                                        while(currentLine.startsWith(leadingWhiteSpacesEnv)) {
                                                            lineNumber++;
                                                            if (currentLine.trim().startsWith("name:")) {
                                                                lines.add(new Line(lineNumber, 1D, true));
                                                                environmentVariable.setKey(currentLine.split("name:")[1].trim());
                                                            } else if (currentLine.trim().startsWith("value:")) {
                                                                lines.add(new Line(lineNumber, 1D, true));
                                                                environmentVariable.setValue(currentLine.split("value:")[1].trim());
                                                            } else {
                                                                lines.add(new Line(lineNumber, 0D, true));
                                                            }                            
                                                            if (linesIterator.hasNext()) {
                                                                currentLine = linesIterator.next();
                                                            } else {
                                                                break;
                                                            }
                                                        }
                                                        if (linesIterator.hasNext()) {
                                                            linesIterator.previous();                                
                                                        }
                                                        environmentVariables.add(environmentVariable);
                                                    }
                                                    if (linesIterator.hasNext()) {
                                                        linesIterator.previous();                                
                                                    }
                                                    container.setEnvironmentVariables(environmentVariables);                                  
                                                } else {
                                                    lines.add(new Line(lineNumber, 0D, true));
                                                }                
                                                if (linesIterator.hasNext()) {
                                                    currentLine = linesIterator.next();
                                                } else {
                                                    break;
                                                }
                                            }
                                            if (linesIterator.hasNext()) {
                                                linesIterator.previous();                                
                                            }
                                            Set<Container> containerSet = kubernetesDeployment.getContainer();
                                            containerSet.add(container);
                                            kubernetesDeployment.setContainer(containerSet);
                                        }
                                        if (linesIterator.hasNext()) {
                                            linesIterator.previous();                                
                                        }
                                    } else {
                                        lines.add(new Line(lineNumber, 0D, true));
                                    }
                                }
                                if (linesIterator.hasNext()) {
                                    linesIterator.previous();                                
                                }
                            } else {                                
                                lines.add(new Line(lineNumber, 0D, true));
                            }
                        }
                        if (linesIterator.hasNext()) {
                            linesIterator.previous();                                
                        }
                    } else {
                        lines.add(new Line(lineNumber, 0D, true));
                    }
                }
                if (linesIterator.hasNext()) {
                    linesIterator.previous();                                
                }
            }else if (currentLine.startsWith("kind:")) {
                lines.add(new Line(lineNumber, 1D, true));
            } else {
                lines.add(new Line(lineNumber, 0D, true));
            }            
            lineNumber++;
        }
        this.deployments.add(kubernetesDeployment);
        return lines;
    }

    
}
