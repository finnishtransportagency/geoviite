# Teknologiat

Käytetyista kirjastoista, versioista ym. kannattaa katsoa ajantasainen tieto versionhallinasta:

Backend: https://github.com/finnishtransportagency/geoviite/blob/main/infra/build.gradle.kts

Frontend: https://github.com/finnishtransportagency/geoviite/blob/main/ui/package.json

Ajoympäristö: https://github.com/finnishtransportagency/geoviite/blob/main/aws/package.json

| Backend                       | Teknologia            | Versio |
|-------------------------------|-----------------------|--------|
| Totetuskieli                  | Kotlin                | 1.4    |
| Framework                     | Spring Boot           | 2.4    |
| Tietokanta                    | PostgreSQL (+PostGIS) | 12     |
| Tietokantamigraatiot          | Flyway                | 8.0    |
| Build & dependency management | Gradle                | 6.8    |
| Yksikkötestaus                | Jupiter (Junit 5)              |        |
| E2E-testaus                   | Selenium/Jupiter/Kotlin                      |        |

| Frontend     | Teknologia | Versio |
|--------------|------------|--------|
| Totetuskieli | TypeScript | 4.2    |
| Framework    | React | 17.0   |
| Muita        | Redux | 7.1    |

| Ajoympäristö | Teknologia |  |
|--|--|--|
| Pilvipalvelu | AWS |        |
| Tietokanta   | AWS Aurora (PostgreSQL) |        |
| Backend      | Kontitettu, AWS Fargate/ECS |        |
| Frontend     | AWS CloudFront + S3 (kehitysympäristössä Webpack dev server) |        |
| Valvonta     | AWS Cloudwatch |        |

| Kehitys/CI/CD | |        |
|--------------|------------|--------|
| Versionhallinta | Väylän Github |        |
| CI/CD | AWS CodeBuild/CodeDeploy |        |
| Ympäristöjen hallinta | CDK (TypeScript) & AWS Cloudformation |        |
