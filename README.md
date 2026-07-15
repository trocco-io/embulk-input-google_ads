# Google Ads input plugin for Embulk

TODO: Write short description here and build.gradle file.

## Overview

* **Plugin type**: input
* **Resume supported**: yes
* **Cleanup supported**: yes
* **Guess supported**: no

## Configuration

- **option1**: description (integer, required)
- **option2**: description (string, default: `"myvalue"`)
- **option3**: description (string, default: `null`)

## Example

```yaml
in:
  type: google_ads
  option1: example1
  option2: example2
```

## Notes on change_event / change_status resources

Queries against `change_event` and `change_status` are filtered by
`change_event.change_date_time` / `change_status.last_change_date_time`
(`daterange` config) and paginate by re-querying from the last row's timestamp
(inclusive), skipping already-emitted rows by resource name.

Keep `limit` at or near the API maximum of 10,000 for these resources. If more
rows than `limit` share one exact timestamp, pagination cannot advance past it
and rows at and after that timestamp are not fetched (an error is logged). A
small `limit` sharply raises the chance of hitting this, since bulk operations
often change many resources within the same second.


## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
