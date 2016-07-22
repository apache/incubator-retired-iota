
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.iota.fey

object Utils_JSONTest {

  val create_json_test =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "CREATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "NONE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         },
         {
            "guid":"MY-ENSEMBLE-0002",
            "command": "NONE",
            "performers":[
              {
                "guid": "TEST-0001",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestActor",
                  "parameters": {}
                }
              }
            ],
            "connections":[]
          }
       ]
     }"""

  val update_json_test =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "UPDATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "UPDATE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             },
             {
                "guid": "TEST-0002",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestActor",
                  "parameters": {}
                }
              }
           ],
           "connections":[]
         }
       ]
     }"""

  val update_delete_json_test =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "UPDATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "DELETE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             },
             {
                "guid": "TEST-0002",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestActor",
                  "parameters": {}
                }
              }
           ],
           "connections":[]
         }
       ]
     }"""

  val delete_json_test =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "DELETE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : []
     }"""

  val recreate_timestamp_json_test =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "RECREATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "DELETE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             },
             {
                "guid": "TEST-0002",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestActor",
                  "parameters": {}
                }
              }
           ],
           "connections":[]
         }
       ]
     }"""

  val orchestration_test_json =
    """{
       "guid" : "TEST-ORCH-2",
       "command" : "CREATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "NONE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         }
       ]
     }"""

  val orchestration_update_test_json =
    """{
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "command": "UPDATE",
           "performers":[
             {
               "guid": "TEST-0004",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             },
             {
               "guid": "TEST-0005",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         }
       ]
     }"""

  val orchestration_update2_test_json =
    """{
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0005",
           "command": "UPDATE",
           "performers":[
             {
               "guid": "TEST-0004",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         }
       ]
     }"""

  val simple_ensemble_test_json =
    """
     {
       "guid":"MY-ENSEMBLE-0005",
       "command": "UPDATE",
       "performers":[
         {
           "guid": "TEST-0004",
           "schedule": 0,
           "backoff": 0,
           "source": {
             "name": "fey-test-actor.jar",
             "classPath": "org.apache.iota.fey.TestActor",
             "parameters": {}
           }
         }
       ],
       "connections":[]
     }
    """

  val ensemble_test_json =
    """
     {
       "guid":"MY-ENSEMBLE-0005",
       "command": "NONE",
       "performers":[
         {
           "guid": "PERFORMER-SCHEDULER",
           "schedule": 200,
           "backoff": 0,
           "source": {
             "name": "fey-test-actor.jar",
             "classPath": "org.apache.iota.fey.TestActor",
             "parameters": {}
           }
         },
          {
           "guid": "PERFORMER-PARAMS",
           "schedule": 0,
           "backoff": 0,
           "source": {
             "name": "fey-test-actor.jar",
             "classPath": "org.apache.iota.fey.TestActor",
             "parameters": {
               "param-1" : "test",
               "param-2" : "test2"
             }
           }
         }
       ],
       "connections":[
        {"PERFORMER-SCHEDULER":["PERFORMER-PARAMS"]}
       ]
     }
    """
  val ensemble_backoff_test_json =
    """
     {
       "guid":"MY-ENSEMBLE-0005",
       "command": "NONE",
       "performers":[
         {
           "guid": "PERFORMER-SCHEDULER",
           "schedule": 200,
           "backoff": 0,
           "autoScale": 2,
           "source": {
             "name": "fey-test-actor.jar",
             "classPath": "org.apache.iota.fey.TestActor",
             "parameters": {}
           }
         },
          {
           "guid": "PERFORMER-PARAMS",
           "schedule": 0,
           "backoff": 1000,
           "source": {
             "name": "fey-test-actor.jar",
             "classPath": "org.apache.iota.fey.TestActor",
             "parameters": {
               "param-1" : "test",
               "param-2" : "test2"
             }
           }
         }
       ],
       "connections":[
        {"PERFORMER-SCHEDULER":["PERFORMER-PARAMS"]}
       ]
     }
    """

  val test_json_schema_invalid =
    """{
       "guid" : "TEST-ACTOR",
       "command" : "CREATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-0001",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         }
       ]
     }"""

  val location_test =
    """{
        "guid": "Orch2",
        "command": "CREATE",
        "timestamp": "591997890",
        "name": "DESCRIPTION",
        "ensembles": [
          {
            "guid": "En2",
            "command": "NONE",
            "performers": [
              {
                "guid": "S2",
                "schedule": 1000,
                "backoff": 0,
                "source": {
                  "name": "fey-stream.jar",
                  "classPath": "org.apache.iota.fey.performer.Timestamp",
                  "location" :{
                    "url" : "https://github.com/apache/incubator-iota/raw/master/fey-examples/active-jar-repo"
                  },
                  "parameters": {
                  }
                }
              }
            ],
            "connections": [
            ]
          }
        ]
      }"""

  val location_test_2 =
    """{
        "guid": "Orch2",
        "command": "CREATE",
        "timestamp": "591997890",
        "name": "DESCRIPTION",
        "ensembles": [
          {
            "guid": "En2",
            "command": "NONE",
            "performers": [
              {
                "guid": "S2",
                "schedule": 1000,
                "backoff": 0,
                "source": {
                  "name": "fey-virtual-sensor.jar",
                  "classPath": "org.apache.iota.fey.performer.Sensor",
                  "location" :{
                    "url" : "https://github.com/apache/incubator-iota/raw/master/fey-examples/active-jar-repo"
                  },
                  "parameters": {
                  }
                }
              }
            ],
            "connections": [
            ]
          }
        ]
      }"""

  val generic_receiver_json = """{
        "guid": "RECEIVER_ORCHESTRATION",
        "command": "CREATE",
        "timestamp": "591997890",
        "name": "DESCRIPTION",
        "ensembles": [
          {
            "guid": "RECEIVER-ENSEMBLE",
            "command": "NONE",
            "performers": [
              {
                "guid": "MY_RECEIVER_PERFORMER",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestReceiverActor",
                  "parameters": {
                  }
                }
              }
            ],
            "connections": [
            ]
          }
        ]
      }"""

  val json_for_receiver_test =
    """{
       "guid" : "RECEIVED-BY-ACTOR-RECEIVER",
       "command" : "CREATE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-REC-0001",
           "command": "NONE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         },
         {
            "guid":"MY-ENSEMBLE-REC-0002",
            "command": "NONE",
            "performers":[
              {
                "guid": "TEST-0001",
                "schedule": 0,
                "backoff": 0,
                "source": {
                  "name": "fey-test-actor.jar",
                  "classPath": "org.apache.iota.fey.TestActor_2",
                  "parameters": {}
                }
              }
            ],
            "connections":[]
          }
       ]
     }"""

  val json_for_receiver_test_delete =
    """{
       "guid" : "RECEIVED-BY-ACTOR-RECEIVER",
       "command" : "DELETE",
       "timestamp": "213263914979",
       "name" : "ORCHESTRATION FOR TEST",
       "ensembles" : [
         {
           "guid":"MY-ENSEMBLE-REC-0001",
           "command": "NONE",
           "performers":[
             {
               "guid": "TEST-0001",
               "schedule": 0,
               "backoff": 0,
               "source": {
                 "name": "fey-test-actor.jar",
                 "classPath": "org.apache.iota.fey.TestActor",
                 "parameters": {}
               }
             }
           ],
           "connections":[]
         }
       ]
     }"""
}
