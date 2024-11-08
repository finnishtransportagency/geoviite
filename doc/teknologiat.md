# Teknologiat

Käytetyista kirjastoista, versioista ym. kannattaa katsoa ajantasainen tieto versionhallinasta:

Backend: https://github.com/finnishtransportagency/geoviite/blob/main/infra/build.gradle.kts

Frontend: https://github.com/finnishtransportagency/geoviite/blob/main/ui/package.json

Ajoympäristö: https://github.com/finnishtransportagency/geoviite/blob/main/aws/package.json

| Backend                       | Teknologia              | Versio |
|-------------------------------|-------------------------|--------|
| Toteutuskieli                 | Kotlin                  | 1.9    |
| Framework                     | Spring Boot             | 3      |
| Tietokanta                    | PostgreSQL (+PostGIS)   | 16 (3) |
| Tietokantamigraatiot          | Flyway                  | 10     |
| Build & dependency management | Gradle                  | 8      |
| Yksikkötestaus                | Jupiter (Junit 5)       |        |
| E2E-testaus                   | Selenium/Jupiter/Kotlin |        |

| Frontend      | Teknologia | Versio |
|---------------|------------|--------|
| Toteutuskieli | TypeScript | 5      |
| Framework     | React      | 18     |
| Muita         | Redux      | 5      |

| Ajoympäristö | Teknologia                                                   |
|--------------|--------------------------------------------------------------|
| Pilvipalvelu | AWS                                                          |
| Tietokanta   | AWS Aurora (PostgreSQL)                                      |
| Backend      | Kontitettu, AWS Fargate/ECS                                  |
| Frontend     | AWS CloudFront + S3 (kehitysympäristössä Webpack dev server) |
| Valvonta     | AWS Cloudwatch                                               |

| Kehitys/CI/CD         |                                       |
|-----------------------|---------------------------------------|
| Versionhallinta       | Väylän Github                         |
| CI/CD                 | AWS CodeBuild/CodeDeploy              |
| Ympäristöjen hallinta | CDK (TypeScript) & AWS Cloudformation |
