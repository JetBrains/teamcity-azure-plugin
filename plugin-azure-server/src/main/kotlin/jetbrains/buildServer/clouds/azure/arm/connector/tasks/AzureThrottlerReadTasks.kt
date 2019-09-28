package jetbrains.buildServer.clouds.azure.arm.connector.tasks

class AzureThrottlerReadTasks {
    enum class Values {
        FetchResourceGroups,
        FetchVirtualMachines,
        FetchInstances,
        FetchCustomImages,
        FetchStorageAccounts,
        FetchVirtualMachineSizes,
        FetchSubscriptions,
        FetchLocations,
        FetchNetworks,
        FetchServices,
    }

    companion object {
        val FetchResourceGroups = AzureTaskDescriptorImpl(Values.FetchResourceGroups, { FetchResourceGroupsMapTaskImpl() })
        val FetchVirtualMachines = AzureTaskDescriptorImpl(Values.FetchVirtualMachines, { FetchVirtualMachinesTaskImpl() })
        val FetchInstances = AzureTaskDescriptorImpl(Values.FetchInstances, { FetchInstancesTaskImpl() })
        val FetchCustomImages = AzureTaskDescriptorImpl(Values.FetchCustomImages, { FetchCustomImagesTaskImpl() })
        val FetchStorageAccounts = AzureTaskDescriptorImpl(Values.FetchStorageAccounts, { FetchStorageAccountsTaskImpl() })
        val FetchVirtualMachineSizes = AzureTaskDescriptorImpl(Values.FetchVirtualMachineSizes, { FetchVirtualMachineSizesTaskImpl() })
        val FetchSubscriptions = AzureTaskDescriptorImpl(Values.FetchSubscriptions, { FetchSubscriptionsTaskImpl() })
        val FetchLocations = AzureTaskDescriptorImpl(Values.FetchLocations, { FetchLocationsTaskImpl() })
        val FetchNetworks = AzureTaskDescriptorImpl(Values.FetchNetworks, { FetchNetworksTaskImpl() })
        val FetchServices = AzureTaskDescriptorImpl(Values.FetchServices, { FetchServicesTaskImpl() })
    }
}
