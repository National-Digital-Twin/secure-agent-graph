# README  

**Repository:** `secure-agent-graph`  
**Description:** `This repository provides the libraries and mechanism for receiving and holding RDF (Resource Description Framework) data. It also provides SPARQL access and integration points such as with ABAC components for security and control over data access.` 

<!-- SPDX-License-Identifier: Apache-2.0 AND OGL-UK-3.0 -->

## Overview  
This repository contributes to the development of **secure, scalable, and interoperable data-sharing infrastructure**. It supports NDTP’s mission to enable **trusted, federated, and decentralised** data-sharing across organisations.  

This repository is one of several open-source components that underpin NDTP’s **Integration Architecture (IA)**—a framework designed to allow organisations to manage and exchange data securely while maintaining control over their own information. The IA is actively deployed and tested across multiple sectors, ensuring its adaptability and alignment with real-world needs. 

For a complete overview of the Integration Architecture (IA) project, please see the [Integration Architecture Documentation](https://github.com/National-Digital-Twin/integration-architecture-documentation).

## Prerequisites  
Before using this repository, ensure you have the following dependencies installed:  
- **Required Tooling:** 
    - Java 21
    - Github PAT token set to allow retrieval of maven packages from Github Packages
    - Docker (if running via docker)
- **Pipeline Requirements:** 
    - Cloud platform credentials
- **Supported Kubernetes Versions:** N/A
- **System Requirements:** 
    - Java 21
    - Docker
    - Kafka (or connectivity to) - if applicable

## Quick Start  
Follow these steps to get started quickly with this repository. For detailed installation, configuration, and deployment, refer to the relevant MD files.  

### 1. Download and Build  
```sh  
git clone https://github.com/National-Digital-Twin/secure-agent-graph.git
cd [secure-agent-graph]  
```
### 2. Run Build Version  
```sh  
mvn clean install --version  

```

### 3. Full Installation  
Refer to [INSTALLATION.md](INSTALLATION.md) for detailed installation steps, including required dependencies and setup configurations.  

### 4. Uninstallation  
For steps to remove this repository and its dependencies, see [UNINSTALL.md](UNINSTALL.md).  

## Features  
- **Key functionality** 
    - Supports secure and RDF (Resource Description Framework) data-sharing.
    - Implements [ABAC (Attribute-Based Access Control)](https://github.com/National-Digital-Twin/rdf-abac/blob/main/docs/abac.md) data security.
- **Key integrations** 
    - Provides SPARQL access using the SPARQL protocol and SPARQL Graph Store Protocol.
    - Integrates with Apache Jena Fuseki server.
    - Includes Fuseki-Kafka bridge for Kafka integration.
    - Offers [GraphQL]((https://github.com/National-Digital-Twin/graphql-jena/blob/main/docs/index.md)) API interfaces.
- **Scalability & performance** 
    - Optimised for high-throughput environments.
    - Supports in-memory datasets for fast data access.
- **Modularity** 
    - Designed with a plugin-based architecture for extensibility.
    - Configurable using Fuseki configuration files and environment variables.

## Testing Guide

### Introduction
This guide aims to detail how to run tests on the various NDTP repositories. These repositories include Java, Python, JavaScript and more so different testing methods are detailed throughout. This document will cover running **unit tests, integration tests and smoke tests**.

### Unit Tests
Unit testing is a software testing method where individual components or modules of an application are tested in isolation to verify their correctness. These components are often the smallest testable parts of the application, such as functions/methods, or class functions.

To perform unit tests, we use SonarQube. This platform allows us to see coverage (and other useful metrics) inside an easy to use yet powerful web interface hosted locally.

### Starting SonarQube Locally
1. *The following command spins up a docker container for a community edition of SonarQube locally (at time of writing **25.5.0.107428** is the latest version).*
   ```shell
   docker run -p 9000:9000 -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true --name sonarqube sonarqube:25.5.0.107428-community
   ```
  > [!NOTE]
  > The **SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true** environment flag. This is to bypass Elasticsearch bootstrap checks as they, are not necessary for a local setup and can sometimes cause issues during setup.

2. Go to your web browser and navigate to http://localhost:9000. This will bring up the login portal for SonarQube. When logging in for the first time use admin for both the username and password. It will ask you to set a new password for which you can choose anything of preference. 
  > [!NOTE]  
  > If you get an **Unexpected application error**, refresh the page (it seems to be to do with an overlay not rendering properly).

3. Select **Create a local project** and give it a name (this should ideally be the exact same name as the repository).

4. On the next page, select the **Use the global setting** option and progress to the next page by clicking **Create project**.

5. To create the token required to analyse the repository with SonarQube, scroll towards the bottom of the **Analysis Method** page and click on Locally.

6. You will be prompted to generate a project token or to use an existing token. For the first time setup select **Generate a project token**, The **Expires in** dropdown will default to 30 days, and then click the **Generate** button.

7. On the next page, click **Continue** and then under the **Run analysis on your project** section, select the option that best describes your project.

> [!NOTE]
> If your project is a Java project, select Maven. If you are unsure whether or not your project is a Java project that will work with the **Maven** method described here, check your root folder for a file called **pom.xml**. If it exists then this method will work.

> [!WARNING]
> If you get an error while running this command, then check that your Java version matches the expected Java version of the repository. The error message should inform you which version is expected.


   - If your project is a Python project, select Python.
  
   - If your project is a JavaScript or TypeScript project, select JavaScript/TypeScript.

8. On selecting, the option that best describes your project, follow the steps shown to run a SonarQube analysis (the command provided must be run at the root of the project folder). 

## API Documentation  
Documentation detailing the relevant configuration and endpoints is provided [here](docs/configuration-secure-agent-graph.md ). 


## Public Funding Acknowledgment  
This repository has been developed with public funding as part of the National Digital Twin Programme (NDTP), a UK Government initiative. NDTP, alongside its partners, has invested in this work to advance open, secure, and reusable digital twin technologies for any organisation, whether from the public or private sector, irrespective of size.  

## License  
This repository contains both source code and documentation, which are covered by different licenses:  
- **Code:** Originally developed by Telicent UK Ltd, now maintained by National Digital Twin Programme. Licensed under the [Apache License 2.0](LICENSE.md).  
- **Documentation:** Licensed under the [Open Government Licence (OGL) v3.0](OGL_LICENSE.md).  

By contributing to this repository, you agree that your contributions will be licensed under these terms.

See [LICENSE.md](LICENSE.md), [OGL_LICENSE.md](OGL_LICENSE.md), and [NOTICE.md](NOTICE.md) for details.  

## Security and Responsible Disclosure  
We take security seriously. If you believe you have found a security vulnerability in this repository, please follow our responsible disclosure process outlined in [SECURITY.md](SECURITY.md).  

## Contributing  
We welcome contributions that align with the Programme’s objectives. Please read our [Contributing](CONTRIBUTING.md) guidelines before submitting pull requests.  

## Acknowledgements  
This repository has benefited from collaboration with various organisations. For a list of acknowledgments, see [ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md).  

## Support and Contact  
For questions or support, check our Issues or contact the NDTP team on ndtp@businessandtrade.gov.uk.

**Maintained by the National Digital Twin Programme (NDTP).**  

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the governing entity.
