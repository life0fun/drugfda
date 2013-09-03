# PDF parser

This project is aim to retrieve pdf documents and extract meaningful text information and store them into ElasticSearch engine for analyze and search.

## Usage
  lein run create-drug-index
  lein run parse pdf-file out-file
  lein run contraindication zocor liver


## Dependencies with Maven local repo

There are many tools for PDF text extraction. For example, Apache pdfbox and PDFTextStream from snowtide.

Leiningen uses Maven as clojure dependency management. However, if you need to depend on jars that are not in a Maven repository, things get a little more complicated. You can checking jars directly into the lib directory, but leiningen would delete these jars whenever you ran “lein deps.”

The solution is to make a local Maven repository within the project and checkin your jars into the local maven repository. To ask lein to search for local repo, add repository into project.clj.

  :repositories {"local" ~(str (.toURI (java.io.File. "maven_repository")))}

  Turned out mvn install does not work for lein 2.0.
    mvn install:install-file -Dfile=pdfbox-app-1.8.2.jar -DartifactId=pdfbox -Dversion=1.8.2 -DgroupId=pdfbox -Dpackaging=jar -DlocalRepositoryPath=maven_repository

  https://gist.github.com/stuartsierra/3062743
  This tells Maven to create the checksums and additional metadata that is expected from a real repository, which makes Leiningen's dependency resolution work again.
  
    mvn deploy:deploy-file -DgroupId=pdfbox -DartifactId=pdfbox \
    -Dversion=1.8.2 -Dpackaging=jar -Dfile=pdfbox-app-1.8.2.jar \
    -Durl=file:maven_repository

  Now lein deps works.