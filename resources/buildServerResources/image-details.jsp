<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="image" type="jetbrains.buildServer.clouds.local.LocalCloudImage" scope="request"/>
Image location: <c:out value="${image.agentHomeDir.absolutePath}"/>

<c:if test="${image.reusable}">
 <br/>
 Instances will be reused. Agent will be un-authorized after stop
</c:if>
