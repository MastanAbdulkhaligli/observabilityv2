# AspectJ Load-Time Weaving Guide

### (Observability Library Integration Manual)

## 1. Purpose

This document explains how to enable **automatic observability instrumentation** using
AspectJ Load-Time Weaving (LTW).

The observability library provides ready-made aspects that automatically instrument:

* Controllers
* Services
* Repositories
* External calls
* Error handling
* Metrics + tracing

To activate them in an application, AspectJ weaving must be enabled.

---

# 2. Why Load-Time Weaving (LTW)?

We support **Load-Time Weaving** instead of compile-time weaving because:

| Reason                        | Explanation                    |
| ----------------------------- | ------------------------------ |
| Library usable by any project | No build modification required |
| Works with Spring Boot        | No special plugin required     |
| Works with legacy apps        | Only JVM agent needed          |
| Zero code changes in app      | Only configuration             |
| Dynamic enable/disable        | Can be toggled via JVM flags   |

Compile-time weaving would require modifying every consumer build — not acceptable for shared libraries.

**Therefore LTW is the official supported mode.**

---

# 3. How It Works

```
Application
   ↓
AspectJ Java Agent (-javaagent)
   ↓
Reads META-INF/aop.xml
   ↓
Loads aspects from observability library
   ↓
Weaves bytecode at class load time
   ↓
Tracing + metrics automatically applied
```

---

# 4. Library Responsibilities (Already Provided)

The observability library already includes:

```
observability-aspectj
 └── META-INF/aop.xml
```

### Library aop.xml (DO NOT MODIFY)

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
        "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.ControllerObservationAspect"/>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.ServiceObservationAspect"/>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.RepositoryObservationAspect"/>
    </aspects>
</aspectj>
```

This file only exposes aspects.
It does NOT decide what application packages to weave.

---

# 5. Application Responsibilities (Required)

Each application using the library must define **where weaving should apply**.

Create file:

```
src/main/resources/META-INF/aop.xml
```

### Application aop.xml

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
        "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>

    <weaver options="-verbose -showWeaveInfo">

        <!-- IMPORTANT: change to your application base package -->
        <include within="com.company.myapp..*"/>

        <!-- Always exclude frameworks -->
        <exclude within="java..*"/>
        <exclude within="javax..*"/>
        <exclude within="jakarta..*"/>
        <exclude within="org.springframework..*"/>
        <exclude within="org.hibernate..*"/>
        <exclude within="io.opentelemetry..*"/>
        <exclude within="org.slf4j..*"/>

    </weaver>

    <aspects>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.ControllerObservationAspect"/>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.ServiceObservationAspect"/>
        <aspect name="az.magusframework.components.lib.aspectj.aspects.RepositoryObservationAspect"/>
    </aspects>

</aspectj>
```

---

# 6. JVM Configuration (MANDATORY)

AspectJ agent must be attached.

### Local run

```
java -javaagent:/path/aspectjweaver.jar -jar app.jar
```

### Spring Boot

```
JAVA_TOOL_OPTIONS="-javaagent:/path/aspectjweaver.jar"
```

### Docker

```
ENV JAVA_TOOL_OPTIONS="-javaagent:/aspectjweaver.jar"
```

Without this → aspects will NOT run.

---

# 7. How to Verify Weaving Works

Enable verbose weaving logs:

```
<weaver options="-verbose -showWeaveInfo">
```

Startup logs should show:

```
[AspectJ] weaving 'UserService'
[AspectJ] Join point matched
```

If not visible:

* agent missing
* wrong package include
* wrong aop.xml location

---

# 8. Common Mistakes

### ❌ Putting <include> inside library aop.xml

Library must not know application packages.

### ❌ Forgetting javaagent

No agent = no weaving.

### ❌ Using wildcard too broad

Never use:

```
<include within="*"/>
```

Will try weaving JDK → crash.

### ❌ Not excluding Spring/Hibernate

May cause performance issues.

---

# 9. Performance Notes

LTW cost:

* startup slightly slower (class weaving)
* runtime overhead minimal (nano-level)
* production safe

Used by:

* Datadog APM
* NewRelic
* Elastic APM
* OpenTelemetry Java agent

---

# 10. Recommended Production Strategy

| Environment         | Weaving  |
| ------------------- | -------- |
| local dev           | enabled  |
| staging             | enabled  |
| production          | enabled  |
| performance testing | enabled  |
| unit tests          | optional |

---

# 11. Future Direction (Optional)

Later we may provide:

* Spring Boot auto-weaving starter
* Agent auto-attach
* Dynamic enable/disable

---

# 12. Summary

Library provides:

* Aspects
* Telemetry logic
* MDC propagation

Application provides:

* Target packages
* JVM agent

This separation allows safe reuse across multiple systems.
