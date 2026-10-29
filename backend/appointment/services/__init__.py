"""Write operations of the appointment app (see HackSoft Django-Styleguide).

booking        create / edit appointments (client and owner paths)
transitions    status changes: cancel, lazy lock expiry, owner decisions
deposits       settling a gateway deposit callback
notifications  outbox-enqueue wrappers and the outbox handler bodies
"""
