package jetbrains.buildServer.clouds.azure.throttler

import jetbrains.buildServer.clouds.azure.arm.throttler.*
import org.jmock.Expectations
import org.jmock.MockObjectTestCase
import org.jmock.Mockery
import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.time.LocalDateTime

class AzureThrottlerStrategyTest : MockObjectTestCase() {
    private var enableAggressiveThrottlingWhenReachLimitInPercents: Int = 0
    private var resourceReservationInPercents: Int = 0
    private var randomTasksResourceReservationInPercents: Int = 0

    private lateinit var mockery: Mockery
    private lateinit var adapter: AzureThrottlerAdapter<Unit>
    private lateinit var container: AzureThrottlerStrategyTaskContainer<String>
    private lateinit var tasks: MutableList<AzureThrottlerStrategyTask<String>>

    @BeforeMethod
    fun beforeMethod() {
        mockery = Mockery()

        adapter = mockery.mock()

        tasks = mutableListOf()

        container = mockery.mock()
    }

    @Test
    fun shouldBeInitializedWithNormalFlow() {
        // Given
        // When
        val instance = createInstance()
        instance.setContainer(container)

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Normal)
    }

    @Test(dataProvider = "testThrottlerTimes")
    fun shouldSetThrottlerTime(defaultReads: Long, remainingReads: Long, aggressiveLimits: Int, windowInMs: Long, expectedTime: Long) {
        // Given
        enableAggressiveThrottlingWhenReachLimitInPercents = aggressiveLimits

        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).getDefaultReads()
                will(returnValue(defaultReads))

                oneOf(adapter).getRemainingReads()
                will(returnValue(remainingReads))

                oneOf(adapter).getWindowStartDateTime()
                will(returnValue(LocalDateTime.parse("2019-10-14T00:00:00")))

                oneOf(adapter).getWindowWidthInMilliseconds()
                will(returnValue(windowInMs))
            }
        })

        mockery.checking(
                object : Expectations() {
                    init {
                        oneOf(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).setThrottlerTime(expectedTime)
            }
        })

        // When
        instance.applyTaskChanges()

        // Then
        mockery.assertIsSatisfied()
    }

    @Test
    fun shouldNotifyCompletedInSuspendedFlow() {
        // Given
        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(
                object : Expectations() {
                    init {
                        allowing(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )

        instance.notifyRateLimitReached(0)

        val task : AzureThrottlerStrategyTask<String> = mockery.mock()
        mockery.checking(
                object : Expectations() {
                    init {
                        oneOf(task).resetCache(AzureThrottlingSource.Throttler)
                    }
                }
        )
        tasks.add(task)

        // When
        instance.notifyCompleted()

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Normal)
        mockery.assertIsSatisfied()
    }

    @Test
    fun shouldNotifyRateLimitInNormalFlow() {
        // Given
        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(
                object : Expectations() {
                    init {
                        allowing(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )

        val task : AzureThrottlerStrategyTask<String> = mockery.mock()
        mockery.checking(
                object : Expectations() {
                    init {
                        oneOf(task).setCacheTimeout(123 + 5, AzureThrottlingSource.Adapter)
                    }
                }
        )
        tasks.add(task)

        // When
        instance.notifyRateLimitReached(123)

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Suspended)
        mockery.assertIsSatisfied()
    }

    @Test
    fun shouldDoNothingWhenNotifyCompletedInNormalFlow() {
        // Given
        val instance = createInstance()
        instance.setContainer(container)

        // When
        instance.notifyCompleted()

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Normal)
        mockery.assertIsSatisfied()
    }

    @Test
    fun shouldNotifyRateLimitInSuspendedFlow() {
        // Given
        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(
                object : Expectations() {
                    init {
                        allowing(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )

        val task : AzureThrottlerStrategyTask<String> = mockery.mock()
        mockery.checking(
                object : Expectations() {
                    init {
                        oneOf(task).setCacheTimeout(0 + 5, AzureThrottlingSource.Adapter)
                        oneOf(task).setCacheTimeout(123 + 5, AzureThrottlingSource.Adapter)
                    }
                }
        )
        tasks.add(task)

        instance.notifyRateLimitReached(0)

        // When
        instance.notifyRateLimitReached(123)

        // Then
        Assert.assertEquals(instance.getFlow(), AzureThrottlerFlow.Suspended)
        mockery.assertIsSatisfied()
    }

    @Test(dataProvider = "testTaskTimeouts")
    fun shouldSetTimeoutForPeriodicalTasks(
            randomTasksStatistics: Array<AzureThrottlerTaskQueueCallHistoryStatistics>,
            periodicalTasksStatistics: Array<AzureThrottlerTaskQueueCallHistoryStatistics>,
            adapterState: AdapterState,
            expectedPeriodicalTasksTimeouts: Array<Long?>
    ) {
        // Given
        randomTasksResourceReservationInPercents = 50
        enableAggressiveThrottlingWhenReachLimitInPercents = 100

        val instance = createInstance()
        instance.setContainer(container)

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).getDefaultReads()
                will(returnValue(adapterState.defaultReads))

                oneOf(adapter).getRemainingReads()
                will(returnValue(adapterState.remainingReads))

                oneOf(adapter).getWindowStartDateTime()
                will(returnValue(adapterState.windowStartDateTime))

                oneOf(adapter).getWindowWidthInMilliseconds()
                will(returnValue(adapterState.windowWidthInMs))
            }
        })

        mockery.checking(
                object : Expectations() {
                    init {
                        allowing(container).getTaskList()
                        will(returnValue(tasks))
                    }
                }
        )

        mockery.checking(object : Expectations() {
            init {
                oneOf(adapter).setThrottlerTime(0)
            }
        })

        randomTasksStatistics.forEachIndexed { index, statistics ->
            val task : AzureThrottlerStrategyTask<String> = mockery.mock("Random - $index")
            mockery.checking(
                    object : Expectations() {
                        init {
                            allowing(task).timeExecutionType
                            will(returnValue(AzureThrottlerTaskTimeExecutionType.Random))

                            allowing(task).taskId
                            will(returnValue("Random - $index"))

                            oneOf(task).getStatistics(adapterState.windowStartDateTime)
                            will(returnValue(statistics))
                        }
                    }
            )
            tasks.add(task)
        }

        periodicalTasksStatistics.forEachIndexed { index, statistics ->
            val task : AzureThrottlerStrategyTask<String> = mockery.mock("Periodical - $index")
            mockery.checking(
                    object : Expectations() {
                        init {
                            allowing(task).timeExecutionType
                            will(returnValue(AzureThrottlerTaskTimeExecutionType.Periodical))

                            allowing(task).taskId
                            will(returnValue("Periodical - $index"))

                            oneOf(task).getStatistics(adapterState.windowStartDateTime)
                            will(returnValue(statistics))

                            expectedPeriodicalTasksTimeouts[index]?.let {
                                oneOf(task).setCacheTimeout(it, AzureThrottlingSource.Throttler)
                            }
                        }
                    }
            )
            tasks.add(task)
        }

        // When
        instance.applyTaskChanges()

        // Then
        mockery.assertIsSatisfied()
    }

    private fun createInstance() : AzureThrottlerStrategyImpl<Unit, String> {
        return AzureThrottlerStrategyImpl(
                adapter,
                randomTasksResourceReservationInPercents,
                resourceReservationInPercents,
                enableAggressiveThrottlingWhenReachLimitInPercents)
    }

    @DataProvider
    private fun testThrottlerTimes(): Array<Array<out Any?>> {
        //defaultReads: Long, remainingReads: Long, aggressiveLimits: Int, windowInMs: Long, expectedTime: Long
        return arrayOf(
                arrayOf(0, 0, 0, 1000, 1000),
                arrayOf(0, 0, 0, 0, 0),
                arrayOf(20, 0, 0, 1234, 1234),
                arrayOf(20, 0, 100, 1234, 1234),

                arrayOf(10, 1, 90, 1234, 1234),
                arrayOf(20, 2, 90, 1234, 1234/2),
                arrayOf(20, 2, 100, 1234, 0),
                arrayOf(20, 20, 90, 1234, 0)
        )
    }

    @DataProvider
    private fun testTaskTimeouts(): Array<Array<out Any?>> {
        //randomTasksStatistics: Array<AzureThrottlerTaskQueueCallHistoryStatistics>,
        //periodicalTasksStatistics: Array<AzureThrottlerTaskQueueCallHistoryStatistics>,
        //adapterState: AdapterState,
        //expectedPeriodicalTasksTimeouts: Array<Long?>
        return arrayOf(
                arrayOf(
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, null, 1),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, null, 2)
                        ),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 20)
                        ),
                        AdapterState(12000, 12000, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(0, 0)
                ),
                arrayOf(
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, null, 2),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, null, 8)
                        ),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 20)
                        ),
                        AdapterState(110, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(5, 10)
                ),
                arrayOf(
                        emptyArray<AzureThrottlerTaskQueueCallHistoryStatistics>(),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 20)
                        ),
                        AdapterState(100, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(5, 10)
                ),
                arrayOf(
                        emptyArray<AzureThrottlerTaskQueueCallHistoryStatistics>(),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 10, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 1, 20)
                        ),
                        AdapterState(100, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(0, 10)
                ),
                arrayOf(
                        emptyArray<AzureThrottlerTaskQueueCallHistoryStatistics>(),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 10, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 2, 20)
                        ),
                        AdapterState(100, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(0, 5)
                ),
                arrayOf(
                        emptyArray<AzureThrottlerTaskQueueCallHistoryStatistics>(),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 10, 0),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 2, 0)
                        ),
                        AdapterState(100, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(0, 0)
                ),
                arrayOf(
                        emptyArray<AzureThrottlerTaskQueueCallHistoryStatistics>(),
                        arrayOf(
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 0, 10),
                                AzureThrottlerTaskQueueCallHistoryStatistics(null, 0, 10)
                        ),
                        AdapterState(100, 70, 10000, LocalDateTime.now()),
                        arrayOf<Long?>(0, 0)
                )
        )
    }

    private inline fun <reified T : Any> Mockery.mock(): T = this.mock(T::class.java)!!
    private inline fun <reified T : Any> Mockery.mock(name: String): T = this.mock(T::class.java, name)!!

    data class AdapterState(val defaultReads: Long, val remainingReads: Long, val windowWidthInMs: Long, val windowStartDateTime: LocalDateTime)
}