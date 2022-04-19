package ust.tad.kubernetesplugin.analysis;

import java.lang.StackWalker.Option;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ust.tad.kubernetesplugin.kubernetesmodel.deployment.KubernetesDeployment;
import ust.tad.kubernetesplugin.kubernetesmodel.service.KubernetesService;
import ust.tad.kubernetesplugin.models.tadm.Component;
import ust.tad.kubernetesplugin.models.tadm.ComponentType;
import ust.tad.kubernetesplugin.models.tadm.Confidence;
import ust.tad.kubernetesplugin.models.tadm.InvalidRelationException;
import ust.tad.kubernetesplugin.models.tadm.Property;
import ust.tad.kubernetesplugin.models.tadm.Relation;
import ust.tad.kubernetesplugin.models.tadm.RelationType;
import ust.tad.kubernetesplugin.models.tadm.TechnologyAgnosticDeploymentModel;

@Service
public class RelationFinderService {
    
    private static final Logger LOG =
      LoggerFactory.getLogger(RelationFinderService.class);

    private RelationType connectsToRelationType = new RelationType();
    private RelationType hostedOnRelationType = new RelationType();

    /**
     * Creates EDMM relations for the newly created components.
     * 
     * @param tadm
     * @param newComponents
     * @param matchingServicesAndDeployments
     * @return
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    public TechnologyAgnosticDeploymentModel findAndCreateRelations(
        TechnologyAgnosticDeploymentModel tadm, 
        List<Component> newComponents, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException, URISyntaxException {
            setRelationTypes(tadm.getRelationTypes());
            List<Relation> newRelations = new ArrayList<>();

            for (Component newComponent : newComponents) {
                newRelations.addAll(findRelationsInProperties(tadm, newComponent, matchingServicesAndDeployments));
                Optional<Relation> relationToContainerRuntime = findRelationToContainerRuntime(tadm, newComponent);
                if (relationToContainerRuntime.isPresent()) {
                    newRelations.add(relationToContainerRuntime.get());
                }
            }        

            List<Relation> relations = tadm.getRelations();
            relations.addAll(newRelations);
            tadm.setRelations(relations);
            return tadm;
    }

    /**
     * Finds and creates an EDMM relation of type "hosted on" between a newly created component and an 
     * already existing component of type "container runtime".
     * 
     * @param tadm
     * @param newComponent
     * @return
     * @throws InvalidRelationException
     */
    private Optional<Relation> findRelationToContainerRuntime(TechnologyAgnosticDeploymentModel tadm, Component newComponent) throws InvalidRelationException {
        Optional<ComponentType> containerRuntimeComponentTypeOpt = tadm.getComponentTypes().stream().filter(componentType -> componentType.getName().equals("container_runtime")).findFirst();
        if (containerRuntimeComponentTypeOpt.isPresent()) {
            Optional<Component> containerRuntimeComponentOpt = tadm.getComponents().stream().filter(component -> component.getType().equals(containerRuntimeComponentTypeOpt.get())).findFirst();
            if(containerRuntimeComponentOpt.isPresent()) {
                LOG.info("Source: "+newComponent.toString());
                LOG.info("Target: "+containerRuntimeComponentOpt.get().toString());
                Relation relation = new Relation();
                relation.setType(this.hostedOnRelationType);
                relation.setName(newComponent.getName()+"_"+this.hostedOnRelationType.getName()+"_"+containerRuntimeComponentOpt.get().getName());
                relation.setSource(newComponent);
                relation.setTarget(containerRuntimeComponentOpt.get());
                relation.setConfidence(Confidence.SUSPECTED);
                return Optional.of(relation);
            }
        }
        return Optional.empty();
    }

    /**
     * Iterates over the properties of a newly created component to find relations to other components.
     * 
     * @param tadm
     * @param newComponent
     * @param matchingServicesAndDeployments
     * @return
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    private List<Relation> findRelationsInProperties(
        TechnologyAgnosticDeploymentModel tadm, 
        Component newComponent, 
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException, URISyntaxException {
            List<Relation> newRelations = new ArrayList<>();

            for (Property property : newComponent.getProperties()) {
                if (property.getKey().equals("SPRING_DATASOURCE_URL")) {
                    URI connectionURI = new URI(property.getValue().toString().replaceFirst("jdbc:", ""));
                    Optional<Relation> relationOpt = createRelationFromConnectionURI(connectionURI, newComponent, tadm.getComponents(), matchingServicesAndDeployments);
                    if (relationOpt.isPresent()) {
                        newRelations.add(relationOpt.get());
                    }
                } else if (property.getValue().toString().startsWith("http")) {
                    URI connectionURI = new URI(property.getValue().toString());
                    Optional<Relation> relationOpt = createRelationFromConnectionURI(connectionURI, newComponent, tadm.getComponents(), matchingServicesAndDeployments);
                    if (relationOpt.isPresent()) {
                        newRelations.add(relationOpt.get());
                    }
                }
            // kafka & kafka-zookeeper -> eventuate, search values and then components with this name? add with helm?
            // mongodb: MONGO_HOST, value has component name but with suffix -mongodb
            }
            return newRelations;
    }

    /**
     * For a given URI, create an EDMM relation of type "connects to".
     * For that, finds the EDMM component that the URI refers to in the host part.
     * If it cannot find the component, it creates no relation.
     * 
     * @param connectionURI
     * @param sourceComponent
     * @param components
     * @param matchingServicesAndDeployments
     * @return
     * @throws MalformedURLException
     * @throws InvalidRelationException
     * @throws URISyntaxException
     */
    private Optional<Relation> createRelationFromConnectionURI(
        URI connectionURI, 
        Component sourceComponent, 
        List<Component> components,
        Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments) 
        throws InvalidRelationException {
            Relation relation = new Relation();
            relation.setType(this.connectsToRelationType);
            relation.setSource(sourceComponent);
            relation.setConfidence(Confidence.CONFIRMED);

            String targetName = connectionURI.getHost();
            Optional<Component> targetComponentOpt = getComponentByName(targetName, components);
            if (targetComponentOpt.isPresent()) {
                relation.setTarget(targetComponentOpt.get());
                relation.setName(sourceComponent.getName()+"_"+this.connectsToRelationType.getName()+"_"+targetComponentOpt.get().getName());
                return Optional.of(relation);
            }
            targetComponentOpt = getComponentByMatchingService(targetName, matchingServicesAndDeployments, components);
            if (targetComponentOpt.isPresent()) {
                relation.setTarget(targetComponentOpt.get());
                relation.setName(sourceComponent.getName()+"_"+this.connectsToRelationType.getName()+"_"+targetComponentOpt.get().getName());
                return Optional.of(relation);
            }

            return Optional.empty();
    }

    private Optional<Component> getComponentByName(String name, List<Component> components) {
        return components.stream().filter(component -> component.getName().equals(name)).findFirst();
    }

    /**
     * For a given serviceName, finds the EDMM component that was created based on this service.
     * 
     * @param serviceName
     * @param matchingServicesAndDeployments
     * @param components
     * @return
     */
    private Optional<Component> getComponentByMatchingService(String serviceName, Map<KubernetesService, KubernetesDeployment> matchingServicesAndDeployments, List<Component> components) {
        Set<KubernetesService> services = matchingServicesAndDeployments.keySet();
        Optional<KubernetesService> matchedService = services.stream().filter(s -> s.getName().equals(serviceName)).findFirst();
        if (matchedService.isPresent()) {
            String componentName = matchingServicesAndDeployments.get(matchedService.get()).getName();
            Optional<Component> component = components.stream().filter(c -> c.getName().equals(componentName)).findFirst();
            if(component.isPresent()) {
                return component;
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the "connects to" and "hosted on" relation types from the technology-agnostic deployment model.
     * Saves them in class-wide variables for reuse in newly created components.
     * 
     * @param relationTypes
     */
    private void setRelationTypes(List<RelationType> relationTypes) {
        Optional<RelationType> connectsToRelationTypeOpt = relationTypes.stream().filter(relationType -> relationType.getName().equals("ConnectsTo")).findFirst();
        if (connectsToRelationTypeOpt.isPresent()) {
            this.connectsToRelationType = connectsToRelationTypeOpt.get();
        }
        Optional<RelationType> hostedOnRelationTypeOpt = relationTypes.stream().filter(relationType -> relationType.getName().equals("HostedOn")).findFirst();
        if (hostedOnRelationTypeOpt.isPresent()) {
            this.hostedOnRelationType = hostedOnRelationTypeOpt.get();
        }
    }
    
}
