const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const normalizeEmail = (email) => String(email || '').trim().toLowerCase();

const isValidEmail = (email) => EMAIL_REGEX.test(normalizeEmail(email));

const normalizePhone = (phone) => {
  const digits = String(phone || '').replace(/\D/g, '');
  if (digits.length === 12 && digits.startsWith('91')) {
    return digits.slice(2);
  }
  return digits;
};

const isValidPhone = (phone) => /^\d{10}$/.test(normalizePhone(phone));

const buildDuplicateFieldMessage = (err) => {
  const duplicateField = Object.keys(err?.keyPattern || {})[0]
    || Object.keys(err?.keyValue || {})[0]
    || 'field';

  if (duplicateField === 'email') {
    return 'Email already registered. Please login.';
  }
  if (duplicateField === 'phone') {
    return 'Phone number already registered. Please login.';
  }
  if (duplicateField === 'staffId') {
    return 'Staff ID collision detected. Please try again.';
  }

  return `${duplicateField} already exists.`;
};

module.exports = {
  EMAIL_REGEX,
  buildDuplicateFieldMessage,
  normalizeEmail,
  isValidEmail,
  normalizePhone,
  isValidPhone,
};
