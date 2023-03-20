# Definition of Terms

This page defines terms used throughout the **Event Hub** documentation. Review of this page is recommended prior to reading through other Event Hub topics.

#### Contents:
- [Event](#event)
- [Event Data](#event-data)
- [Event Hub](#event-hub)
- [Event Listener](#event-listener)
- [Event Source](#event-source)
- [Event Type](#event-type)
- [Extension](#extension)
- [Pending Shared State](#pending-shared-state)
- [Shared State](#shared-state)
- [Shared State Resolver](#shared-state-resolver)

--------------------------------------------------------------------------------
### [Event](Event.md)

The object used to pass data through the **Event Hub**. An Event is defined by a combination of its **Event Data**, **Event Source**, and **Event Type**. An Event contains the data necessary for registered **Extensions** to determine if and how they should respond to the Event. Events can originate externally from public API calls, or internally from the Event Hub or registered Extensions.

--------------------------------------------------------------------------------
### Event Data            

A immutable map (`Map<String, Object>`) of values containing data specific to the **Event**. The Event Data helps interested **Extensions** know _how_ the Event should be handled.

--------------------------------------------------------------------------------
### Event Hub

The controller of the SDK. The Event Hub is responsible for receiving **Events**, maintaining their correct order, and passing them along to any interested **Extension**.

--------------------------------------------------------------------------------
### Event Listener

A mechanism by which an **Extension** can indicate to the **Event Hub** that it is interested in knowing about **Events** that match a specific criteria. Event Listeners are unique per Extension, **Event Source**, and **Event Type**.

--------------------------------------------------------------------------------
### Event Source

A value that indicates the reason for the **Event**'s creation.

E.g. - `com.adobe.eventSource.requestContent`

--------------------------------------------------------------------------------
### Event Type

A value that indicates from which **Extension** an **Event** originated.

E.g. - `com.adobe.eventType.identity`

--------------------------------------------------------------------------------
### Extension

An independent collection of code that can be registered by the **Event Hub**. Extensions do a majority of the work in the SDK. An Extension is responsible for registering one or more **Event Listeners** with the **Event Hub**.

--------------------------------------------------------------------------------
### Pending Shared State

A **Shared State** that has been created, but not yet been populated by the **Extension** that owns it. A Pending Shared State is a way for an Extension to indicate that it will have a valid Shared State for this **Event** in the future. An Extension should create a pending shared state when it knows it will have data to share, but has some other task to complete first before the state can be generated (e.g. - getting a response from an asynchronous network call). Once the Shared State data has been acquired, Pending Shared States should be resolved with a **Shared State Resolver**.

--------------------------------------------------------------------------------
### Shared State

A mechanism that allows **Extensions** to share state data with other Extensions. Any data existing in a shared state is valid  until it is either overwritten or removed by the owning extension. Shared states are owned by the **Event Hub**, but maintained by the Extension that owns them.

--------------------------------------------------------------------------------

### XDM Shared State

A mechanism that allows **Extensions** to share state XDM compliant data with other Extensions. XDM shared states allow the Edge extension to collect XDM data from various mobile extensions when needed and allow for the creation of XDM data elements to be used in Launch rules.

--------------------------------------------------------------------------------

### Shared State Resolver

A [Shared State Resolver](../../code/core/src/main/java/com/adobe/marketing/mobile/SharedStateResolver.java) is used when an **Extension** knows it will need to create a **Shared State** for a specific event, but it doesn't yet have the data that it needs to share. Once the extension has completed its work, it calls the shared state resolver with the necessary data. Shared State Resolvers are used in conjunction with **Pending Shared States**.
