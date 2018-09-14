# smsproxy

tiny android app that runs a server, listens on 1993, and sends sms messages

![Screenshot](https://i.imgur.com/eB6Ou4a.png)

Input Example:

```
{
	"to": "07468430881",
	"message": "KiBook Automated Text Via Andrews Phone",
	"auth": "30eb0c0a-b103-4438-8bae-c64b4cc94924llllllllll"
}
```


Output Example:

```
HTTP/1.1 200 OK
Content-Type: application/json

{"result": "Request Received"}
```
