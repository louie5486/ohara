const { validateUrl, validatePort } = require('../configHelpers');

/* eslint-disable no-console */
describe('ValidatePort()', () => {
  it('throws when given the wrong type', () => {
    // wrap the test in a function so it works as it should https://jestjs.io/docs/en/expect.html#tothrowerror
    expect(() => {
      validatePort('abc');
    }).toThrow(/can only accept number/);
  });

  it('throws when given the invalid port numbers', () => {
    expect(() => {
      validatePort(-1);
    }).toThrow(/is invalid/);

    expect(() => {
      validatePort(0);
    }).toThrow(/is invalid/);

    expect(() => {
      validatePort(16478932479382794);
    }).toThrow(/is invalid/);
  });

  it('works when given the correct type', () => {
    expect(() => {
      validatePort(1234);
    }).not.toThrow();
    expect(() => {
      validatePort(4321);
    }).not.toThrow();
  });
});

describe('validateUrl()', () => {
  jest.spyOn(process, 'exit').mockImplementation(number => number);
  jest.spyOn(console, 'log').mockImplementation(str => str);

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('throws when given the wrong URL', () => {
    validateUrl(123);
    expect(process.exit).toHaveBeenCalledTimes(1);
    expect(process.exit).toHaveBeenCalledWith(1);
    expect(console.log).toHaveBeenCalledTimes(1);
  });

  it('throws when given the invalid URLs', () => {
    validateUrl('http://www.abc:1234');
    expect(process.exit).toHaveBeenCalledTimes(1);
    expect(process.exit).toHaveBeenCalledWith(1);
    expect(console.log).toHaveBeenCalledTimes(1);
  });

  it('works when given the correct URL', () => {
    validateUrl('http://localhost:5050/v0');
    expect(process.exit).not.toBeCalled();
    expect(console.log).not.toBeCalled();
  });
});
