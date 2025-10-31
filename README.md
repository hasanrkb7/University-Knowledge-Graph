# University Knowledge Graph

This is my coursework from 2 courses at the University of Jyväskylä, from September to October 2025. The courses are Semantic Technologies for Developers (TIES4520) and Semantic Web and Linked Data.

## semantic-technologies

| Folder | What it is |
|---|---|
| `task2-rdf-basics` | People, jobs and employment in RDF/XML, and a TriG file with named graphs |
| `task3-owl-ontology` | A small OWL ontology (Turtle and RDF/XML) with individuals |
| `task4-n3-rules` | A family ontology and N3 rules that find new facts, for example step-children |
| `task5-cv-as-linked-data` | My CV as linked data in 4 formats (Turtle, JSON-LD, RDFa in XHTML and plain HTML), with a small CV ontology |
| `semifinal-university` | The semi-final project, explained below |

### Semi-final project

This is a university ontology with 2 SPARQL endpoints.

- Ontology and data: a university ontology with students, staff, courses and departments in OWL, RDF/XML and Turtle. There are also 2 HTML pages with the same sample students, one plain and one with RDFa. The people in them are not real.
- N3 rules: 5 rules (senior supervisor, course colleagues, department members, advanced student and prerequisites), each with its reasoning output.
- `rdf4j-sparql-endpoint`: a Java servlet that loads the data into an RDF4J repository and runs SELECT, ASK and CONSTRUCT queries from a web form.
- `jena-rdfa-endpoint`: a Java servlet with Apache Jena. It loads RDF from a local file or a URL (RDFa pages too), can apply N3 rules first, and then runs a SPARQL query. It turns off the HTTPS certificate checks so it can load any test URL, so only use it for a demo.

These 2 folders are the flat copies I submitted. In the original Maven projects, the servlet is under `src/main/java` and the other files are under `src/main/webapp`.

## linked-data-cv-ontology

This is the final assignment for Semantic Web and Linked Data. It is an OWL ontology to describe a CV (education, jobs and skills), and I filled it with my own CV.

In this repository, I replaced my phone number with `+880-XXXXXXXXXX` and my email address with `remon@example.com`.
