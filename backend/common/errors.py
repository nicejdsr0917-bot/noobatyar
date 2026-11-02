"""Error-message helpers shared by every app's views."""


def extract_error(errors: dict) -> str:
    """اولین پیام خطا رو از serializer errors بیرون میکشه"""
    for field, messages in errors.items():
        if isinstance(messages, list) and messages:
            return f"{messages[0]}"
        if isinstance(messages, str):
            return messages
    return 'اطلاعات نامعتبر'
