# STREAM

The Purpose of the stream performer is to create a stream of output messages.
Each instance of a performer will generate a stream from one of the following types

## Types of stream

1. **Heartbeat** outputs the string "alive"
2. **Timestamp** outputs an epoch timestamp in milliseconds e.g. 1468378490974
3. **RandomInteger**  outputs a random integer e.g. -1146318359
4. **RandomDouble** outputs a random double e.g. 0.6905682320596791
5. **RandomUUID**  outputs a random UUID e.g. 2905d0ce-fe0f-46b5-b94e-77e38ccff789

## Example Orchestration

Note this Performer requires you to set a "schedule". In the example below we
that the schedule is set to 1000 milliseconds (1 second). if you don't set the schedule the
performer will not send any output message. In this example you might want to set the
log-level=debug in your configuration file so you can see the messages that are being generated.

```json
{
  "guid": "Orchestration",
  "command": "CREATE",
  "timestamp": "591997890",
  "name": "DESCRIPTION",
  "ensembles": [
    {
      "guid": "Ensemble",
      "command": "NONE",
      "performers": [
        {
          "guid": "Timestamp",
          "schedule": 1000,
          "backoff": 0,
          "source": {
            "name": "fey-stream.jar",
            "classPath": "org.apache.iota.fey.performer.Timestamp",
            "parameters": {
            }
          }
        }
      ],
      "connections": [
      ]
    }
  ]
}
```