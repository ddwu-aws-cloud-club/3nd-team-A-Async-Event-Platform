const Navbar = ({ userName }) => {
  return (
    <nav style={{ padding: '15px 40px', display: 'flex', justifyContent: 'space-between', backgroundColor: '#3E2723', color: 'white' }}>
      <div style={{ fontWeight: 'bold', fontSize: '20px' }}>Welcome to Async-Event-Platform Platform!</div>
      <div>
        {userName ? (
          <span>Welcome, <strong>{userName}</strong>님!</span>
        ) : (
          <button onClick={() => window.location.href='/login'}>로그인</button>
        )}
      </div>
    </nav>
  );
};

export default Navbar;