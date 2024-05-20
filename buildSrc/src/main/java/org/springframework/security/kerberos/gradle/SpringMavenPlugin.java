/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.security.kerberos.gradle;

import java.util.List;
import java.util.ListIterator;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.VariantVersionMappingStrategy;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPomDeveloperSpec;
import org.gradle.api.publish.maven.MavenPomIssueManagement;
import org.gradle.api.publish.maven.MavenPomLicenseSpec;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

import groovy.util.Node;

public class SpringMavenPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		PluginManager pluginManager = project.getPluginManager();
		pluginManager.apply(MavenPublishPlugin.class);
		pluginManager.apply(SpringSigningPlugin.class);
		pluginManager.apply(PublishLocalPlugin.class);
		pluginManager.apply(PublishAllJavaComponentsPlugin.class);
		pluginManager.apply(PublishArtifactsPlugin.class);

		project.getPlugins().withType(MavenPublishPlugin.class).all(mavenPublish -> {
			PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
			publishing.getPublications().withType(MavenPublication.class)
				.all(mavenPublication -> customizeMavenPublication(mavenPublication, project));
			project.getPlugins().withType(JavaPlugin.class).all(javaPlugin -> {
				JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
				extension.withJavadocJar();
				extension.withSourcesJar();
			});
		});
	}

	private void customizeMavenPublication(MavenPublication publication, Project project) {
		customizePom(publication.getPom(), project);
		project.getPlugins().withType(JavaPlugin.class)
				.all(javaPlugin -> customizeJavaMavenPublication(publication, project));
		suppressMavenOptionalFeatureWarnings(publication);
	}

	private void customizeJavaMavenPublication(MavenPublication publication, Project project) {
		addMavenOptionalFeature(project);
		publication.versionMapping(strategy -> strategy.usage(Usage.JAVA_API, mappingStrategy -> mappingStrategy
				.fromResolutionOf(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)));
		publication.versionMapping(
				strategy -> strategy.usage(Usage.JAVA_RUNTIME, VariantVersionMappingStrategy::fromResolutionResult));
	}

	private void suppressMavenOptionalFeatureWarnings(MavenPublication publication) {
		publication.suppressPomMetadataWarningsFor("mavenOptionalApiElements");
		publication.suppressPomMetadataWarningsFor("mavenOptionalRuntimeElements");
	}

	private void addMavenOptionalFeature(Project project) {
		JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
		extension.registerFeature("mavenOptional",
				feature -> feature.usingSourceSet(extension.getSourceSets().getByName("main")));
		AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents()
				.findByName("java");
		if (javaComponent != null) {
			javaComponent.addVariantsFromConfiguration(
				project.getConfigurations().findByName("mavenOptionalRuntimeElements"),
				ConfigurationVariantDetails::mapToOptional);
		}
	}

	private void customizePom(MavenPom pom, Project project) {
		pom.getUrl().set("https://github.com/spring-projects/spring-security-kerberos");
		pom.getName().set(project.provider(project::getName));
		pom.getDescription().set(project.provider(project::getDescription));
		pom.organization(this::customizeOrganization);
		pom.licenses(this::customizeLicences);
		pom.developers(this::customizeDevelopers);
		pom.scm(scm -> customizeScm(scm, project));
		pom.issueManagement(this::customizeIssueManagement);

		// TODO: find something better not to add dependencyManagement in pom
		//       which result spring-security-kerberos-management in it. spring-security-kerberos-bom
		//       has its own dependencyManagement which we need to keep
		if (!"spring-security-kerberos-bom".equals(project.getName())) {
			pom.withXml(xxx -> {
				Node pomNode = xxx.asNode();
				List<?> childs = pomNode.children();
				ListIterator<?> iter = childs.listIterator();
				while (iter.hasNext()) {
					Object next = iter.next();
					if (next instanceof Node) {
						if ("{http://maven.apache.org/POM/4.0.0}dependencyManagement".equals(((Node)next).name().toString())) {
							iter.remove();
						}
					}
				}
			});
		}
	}

	private void customizeOrganization(MavenPomOrganization organization) {
		organization.getName().set("Pivotal Software, Inc.");
		organization.getUrl().set("https://spring.io");
	}

	private void customizeLicences(MavenPomLicenseSpec licences) {
		licences.license(licence -> {
			licence.getName().set("Apache License, Version 2.0");
			licence.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0");
		});
	}

	private void customizeDevelopers(MavenPomDeveloperSpec developers) {
		developers.developer(developer -> {
			developer.getName().set("Pivotal");
			developer.getEmail().set("info@pivotal.io");
			developer.getOrganization().set("Pivotal Software, Inc.");
			developer.getOrganizationUrl().set("https://www.spring.io");
		});
	}

	private void customizeScm(MavenPomScm scm, Project project) {
		scm.getConnection().set("scm:git:git://github.com/spring-projects/spring-security-kerberos.git");
		scm.getDeveloperConnection().set("scm:git:ssh://git@github.com/spring-projects/spring-security-kerberos.git");
		scm.getUrl().set("https://github.com/spring-projects/spring-security-kerberos");
	}

	private void customizeIssueManagement(MavenPomIssueManagement issueManagement) {
		issueManagement.getSystem().set("GitHub");
		issueManagement.getUrl().set("https://github.com/spring-projects/spring-security-kerberos/issues");
	}
}
