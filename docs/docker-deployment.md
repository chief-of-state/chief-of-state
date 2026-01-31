# 🐳 Docker Deployment

This guide describes deploying Chief of State in Docker, including local development with Docker Compose.

## Docker-Specific Environment Variables

In addition to the [general configuration](./configuration.md), you can set these environment variables:

| Environment Variable | Description                                                                | Default      |
|----------------------|----------------------------------------------------------------------------|--------------|
| COS_DEPLOYMENT_MODE  | Deployment mode (use `"docker"`)                                           | `"docker"`   |
| COS_SERVICE_NAME     | Name of the Chief of State service in your Docker Compose file             | chiefofstate |
| COS_REPLICA_COUNT    | Number of replicas to wait for before starting (not recommended to change) | 1            |
