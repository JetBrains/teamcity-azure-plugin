/*
 * Copyright 2000-2016 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.TeamCityRuntimeException;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.IdProvider;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector;
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance;
import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Sergey.Pak
 *         Date: 7/31/2014
 *         Time: 5:18 PM
 */
public class AzureCloudImage extends AbstractCloudImage<AzureCloudInstance, AzureCloudImageDetails> {

    private static final Logger LOG = Logger.getInstance(AzureCloudImage.class.getName());

    private final AzureCloudImageDetails myImageDetails;
    private final AzureApiConnector myApiConnector;
    private final IdProvider myIdProvider;

    protected AzureCloudImage(@NotNull final AzureCloudImageDetails imageDetails,
                              @NotNull final AzureApiConnector apiConnector,
                              @NotNull final IdProvider idProvider) {
        super(imageDetails.getSourceName(), imageDetails.getSourceName());
        myImageDetails = imageDetails;
        myApiConnector = apiConnector;
        myIdProvider = idProvider;

        final Map<String, AzureInstance> instances = apiConnector.listImageInstances(this);
        for (AzureInstance azureInstance : instances.values()) {
            final String name = azureInstance.getName();
            final AzureCloudInstance cloudInstance = new AzureCloudInstance(this, name, name);
            cloudInstance.setStatus(azureInstance.getInstanceStatus());
            myInstances.put(name, cloudInstance);
        }
    }

    public AzureCloudImageDetails getImageDetails() {
        return myImageDetails;
    }

    @Override
    public AzureCloudInstance getCloudInstance(@NotNull String name) {
        return new AzureCloudInstance(this, name);
    }

    @Override
    public boolean canStartNewInstance() {
        return myInstances.size() < myImageDetails.getMaxInstances();
    }

    @Override
    public void terminateInstance(@NotNull final AzureCloudInstance instance) {
        instance.setStatus(InstanceStatus.STOPPING);

        try {
            myApiConnector.deleteVm(instance);
            instance.setStatus(InstanceStatus.STOPPED);
        } catch (Exception e) {
            instance.setStatus(InstanceStatus.ERROR);
            throw new RuntimeException(e);
        }

        myInstances.remove(instance.getInstanceId());
    }

    @Override
    public void restartInstance(@NotNull final AzureCloudInstance instance) {
        instance.setStatus(InstanceStatus.RESTARTING);

        try {
            myApiConnector.restartVm(instance);
            myApiConnector.updateVmStatus(instance);
        } catch (Exception e) {
            instance.setStatus(InstanceStatus.ERROR);
            throw new RuntimeException(e);
        }
    }

    @Override
    public AzureCloudInstance startNewInstance(@NotNull final CloudInstanceUserData tag) {
        if (!canStartNewInstance()) {
            throw new TeamCityRuntimeException("Unable to start more instances. Limit reached");
        }

        final String vmName = String.format("%s-%d", myImageDetails.getVmNamePrefix(), myIdProvider.getNextId());
        final AzureCloudInstance instance = new AzureCloudInstance(this, vmName);
        instance.setStatus(InstanceStatus.SCHEDULED_TO_START);
        instance.refreshStartDate();

        try {
            myApiConnector.createVm(instance, tag);
            myApiConnector.updateVmStatus(instance);
        } catch (Exception e) {
            instance.setStatus(InstanceStatus.ERROR);
            throw new RuntimeException(e);
        }

        myInstances.put(instance.getInstanceId(), instance);

        return instance;
    }
}