# ☸️ Kubernetes Deployment

Chief of State uses the [Pekko Kubernetes integration](https://pekko.apache.org/docs/pekko-management/current/kubernetes-deployment/) for cluster bootstrap and node discovery. Below are common configuration options.

## Environment Variables

In addition to the [general configuration](./configuration.md), you can set these environment variables:

| Environment Variable     | Description                                                                                            |
|--------------------------|--------------------------------------------------------------------------------------------------------|
| COS_DEPLOYMENT_MODE      | Set to `"kubernetes"` so Chief of State uses the Kubernetes API                                        |
| POD_IP                   | IP of the pod running Chief of State (see note below)                                                  |
| COS_KUBERNETES_APP_LABEL | App label of the Kubernetes pod, used to discover sibling nodes in the cluster                         |
| COS_REPLICA_COUNT        | Must match the replica count in your deployment. Default: `"1"`                                        |

**POD_IP** can be set dynamically with:

```yaml
env:
  - name: POD_IP
    valueFrom:
      fieldRef:
        apiVersion: v1
        fieldPath: status.podIP
```

## Service Account and Role

Chief of State uses the Kubernetes API to discover sibling nodes. Your pods need these permissions:

- **get**
- **watch**
- **list**

Create a Service Account, Role, and RoleBinding:

```yaml
# Role to read pods
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: pod-reader
  namespace: default
rules:
  - apiGroups: [ "" ]
    resources: [ "pods" ]
    verbs: [ "get", "watch", "list" ]

---

# Service account for Chief of State
apiVersion: v1
kind: ServiceAccount
metadata:
  name: chief-of-state-sa
  namespace: default

---

# Bind the role to the service account
apiVersion: rbac.authorization.k8s.io/v1beta1
kind: RoleBinding
metadata:
  name: chief-of-state-sa-pod-reader
  namespace: default
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
subjects:
  - kind: ServiceAccount
    name: chief-of-state-sa
    namespace: default
```

Then assign the service account to your deployment:

```yaml
apiVersion: "apps/v1"
kind: Deployment
metadata:
  name: my-app-chief-of-state
  namespace: default
  labels:
    app: my-app-chief-of-state
spec:
  selector:
    matchLabels:
      app: my-app-chief-of-state
  template:
    metadata:
      labels:
        app: my-app-chief-of-state
    spec:
      serviceAccountName: chief-of-state-sa
```

## Readiness and Liveness Probes

Chief of State provides readiness and liveness probes. Configure them like this:

```yaml
readinessProbe:
  httpGet:
    path: /ready
    port: management
  periodSeconds: 10
  failureThreshold: 3
  initialDelaySeconds: 10
livenessProbe:
  httpGet:
    path: /alive
    port: management
  periodSeconds: 10
  failureThreshold: 5
  initialDelaySeconds: 20
ports:
  - name: management
    containerPort: 8558
    protocol: TCP
```

## Resource Allocations

Always set Kubernetes [resource requests and limits](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#requests-and-limits) for CPU and memory.

By default, Chief of State uses these JVM settings:

- `-Xms300m` — Initial heap size (base memory when idle)
- `-XX:MinRAMPercentage` — Minimum heap as a percentage of container memory
- `-XX:MaxRAMPercentage` — Maximum heap as a percentage of container memory

To override JVM options, set the `JAVA_OPTS` environment variable on the container:

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xms300m -XX:MinRAMPercentage=60.0 -XX:MaxRAMPercentage=90.0 -XX:+HeapDumpOnOutOfMemoryError"
resources:
  limits:
    memory: 512Mi
  requests:
    memory: 256Mi
```
