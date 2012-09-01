from tornado.ioloop import IOLoop
from tornado.database import Connection
import tornado.web as web
import tornadio2

import hashlib
import random

APP_PORT = 8099
SESSION_COOKIE_NAME = "session"

connections = []

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

class WebHandler(web.RequestHandler):
	def get(self, *args, **kw):
		self.render("index.html")

class EventHandler(tornadio2.SocketConnection):
	def on_open(self, request):
		print "client connected."
		connections.append(self)

	def on_close(self):
		print "connection closed."
		connections.remove(self)

	@tornadio2.event
	def move(self, amount):
		pass

class WebApp(object):
	def __init__(self):
		app_router = tornadio2.TornadioRouter(EventHandler)

		routes = [
			(r"/static/(.*)", web.StaticFileHandler, {"path": "./static"}),
			(r"/", WebHandler),
		]

		routes.extend(app_router.urls)

		self.application = web.Application(routes, socket_io_port = APP_PORT, debug = 1)

	def start(self, port = APP_PORT):
		self.application.listen(port)

WebApp().start()
IOLoop.instance().start()

