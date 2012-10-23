from tornado.ioloop import IOLoop
from tornado.database import Connection
import tornado.web as web
import tornadio2

import hashlib
import random
import json
import shelve

APP_PORT = 8099
SESSION_COOKIE_NAME = "session"

connections = []

db = shelve.open("data")

if "last-image" not in db:
	db["last-image"] = 1

image_id = db["last-image"]


def create_random_str():
	return hashlib.sha256(str(random.getrandbits(1000))).hexdigest()

class SessionManager:
	sessions = {}

	@classmethod
	def get(cls, handler):
		session_id = handler.get_cookie(SESSION_COOKIE_NAME)
		if (not session_id) or (session_id not in cls.sessions.keys()):
			session_id = create_random_str()
			while session_id in cls.sessions.keys():
				session_id = create_random_str()
			handler.set_cookie(SESSION_COOKIE_NAME, session_id)
			cls.sessions[session_id] = {}

		return cls.sessions[session_id]

	@classmethod
	def get_by_session_id(cls, session_id):
		return cls.sessions.get(session_id)


class KeepAliveHandler(web.RequestHandler):
	def get(self, *args, **kw):
		for connection in connections:
			connection.emit("keep_alive", "keep_alive")

		print "keepAlive"
		self.render("index.html")
		
class LogHandler(web.RequestHandler):
	def get(self, *args, **kw):
		for connection in connections:
			connection.emit("got_log", self.get_argument("s"))

		print "log: %s" % self.get_argument("s")
		
class WebHandler(web.RequestHandler):
	def get(self, *args, **kw): 
		self.render("index.html")

	def post(self):
		global image_id
		print len(self.request.body)
		open("static/image%d.jpg" % image_id,"wb").write(self.request.body)
		for connection in connections:
			print "disp_img %s" % connection
			connection.emit("disp_img", "static/image%d.jpg" % image_id)
		image_id += 1

class GalleryHandler(web.RequestHandler):
	def get(self, *args, **kw):
		self.render("gallery.html")

class LastImageHandler(web.RequestHandler):
	def get(self, *args, **kw):
		self.write(json.dumps({
			"last-image" : image_id - 1
		}))

class EventHandler(tornadio2.SocketConnection):
	def on_open(self, request):
		print "client connected."
		connections.append(self)

	def on_close(self):
		print "connection closed."
		connections.remove(self)

	def emit_all(self, event, type):
		for connection in connections:
			print "connection: %s" % connection
			connection.emit("action", event, type)
	
	@tornadio2.event
	def down(self, event):
		print "down: %s" % event
		self.emit_all(event, "down")

	@tornadio2.event
	def up(self, event):
		print "up: %s" % event
		self.emit_all(event, "up")

	@tornadio2.event
	def log(self, s):
		print "log: %s" % s
		self.emit_all("got_log", s)

class WebApp(object):
	def __init__(self):
		app_router = tornadio2.TornadioRouter(EventHandler)

		routes = [
			(r"/static/(.*)", web.StaticFileHandler, {"path": "./static"}),
			(r"/", WebHandler),
			(r"/keepalive", KeepAliveHandler),
			(r"/log", LogHandler),
			(r"/gallery", GalleryHandler),
			(r"/last-image", LastImageHandler),

		]

		routes.extend(app_router.urls)

		self.application = web.Application(routes, socket_io_port = APP_PORT, debug = 1)

	def start(self, port = APP_PORT):
		self.application.listen(port)

WebApp().start()
IOLoop.instance().start()

